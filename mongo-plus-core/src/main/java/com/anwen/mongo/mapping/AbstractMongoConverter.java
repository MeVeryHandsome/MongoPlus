package com.anwen.mongo.mapping;

import com.anwen.mongo.annotation.ID;
import com.anwen.mongo.annotation.collection.CollectionField;
import com.anwen.mongo.bson.MongoPlusDocument;
import com.anwen.mongo.cache.global.ConversionCache;
import com.anwen.mongo.cache.global.HandlerCache;
import com.anwen.mongo.cache.global.MappingCache;
import com.anwen.mongo.cache.global.PropertyCache;
import com.anwen.mongo.constant.SqlOperationConstant;
import com.anwen.mongo.context.MongoTransactionContext;
import com.anwen.mongo.enums.FieldFill;
import com.anwen.mongo.enums.IdTypeEnum;
import com.anwen.mongo.handlers.ReadHandler;
import com.anwen.mongo.handlers.TypeHandler;
import com.anwen.mongo.handlers.collection.AnnotationOperate;
import com.anwen.mongo.incrementer.id.IdWorker;
import com.anwen.mongo.logging.Log;
import com.anwen.mongo.logging.LogFactory;
import com.anwen.mongo.manager.MongoPlusClient;
import com.anwen.mongo.model.AutoFillMetaObject;
import com.anwen.mongo.strategy.conversion.ConversionStrategy;
import com.anwen.mongo.strategy.mapping.MappingStrategy;
import com.anwen.mongo.toolkit.BsonUtil;
import com.anwen.mongo.toolkit.ClassTypeUtil;
import com.anwen.mongo.toolkit.CollUtil;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 抽象的映射处理器
 * @author JiaChaoYang
 * @date 2024/5/1 下午6:22
 */
public abstract class AbstractMongoConverter implements MongoConverter {

    private final Log log = LogFactory.getLog(AbstractMongoConverter.class);

    private final MongoPlusClient mongoPlusClient;

    public AbstractMongoConverter(MongoPlusClient mongoPlusClient){
        this.mongoPlusClient = mongoPlusClient;
    }

    public final Map<Class<?>,Boolean> classEnumTypeMap = new ConcurrentHashMap<>();

    //定义添加自动填充字段
    private final AutoFillMetaObject insertFillAutoFillMetaObject = new AutoFillMetaObject();
    private final AutoFillMetaObject updateFillAutoFillMetaObject = new AutoFillMetaObject();

    @Override
    public void writeBySave(Object sourceObj, Document document) {
        // Map类型不需要再做下边的操作 因为它们只针对实体类
        if (ClassTypeUtil.isTargetClass(Map.class,sourceObj.getClass())){
            write((Map<?,?>) sourceObj,document);
            return;
        }
        //封装class信息
        TypeInformation typeInformation = TypeInformation.of(sourceObj);
        //如果存在元对象处理器，且插入或更新字段为空，则获取自动填充字段
        if (HandlerCache.metaObjectHandler != null && insertFillAutoFillMetaObject.isEmpty()){
            //获取所有自动填充数据
            getFillInsertAndUpdateField(typeInformation,insertFillAutoFillMetaObject,updateFillAutoFillMetaObject);
        }
        //拿到类中的@ID字段
        FieldInformation idFieldInformation = typeInformation.getAnnotationField(ID.class);
        if (idFieldInformation != null) {
            //如果没有设置
            Object idValue = idFieldInformation.getValue();
            if (idValue != null) {
                if (ObjectId.isValid(String.valueOf(idValue)) && !idValue.getClass().equals(ObjectId.class)) {
                    document.put(SqlOperationConstant._ID, new ObjectId(String.valueOf(idValue)));
                }
            } else {
                idValue = generateId(idFieldInformation.getId().type(), typeInformation);
            }
            if (idValue != null) {
                idValue = idValue instanceof ObjectId ? idValue : convertValue(idValue, idFieldInformation.getTypeClass());
                if (PropertyCache.objectIdConvertType && idValue instanceof ObjectId) {
                    idValue = convertValue(idValue,idFieldInformation.getTypeClass());
                }
                document.put(SqlOperationConstant._ID, idValue);
                //为自行设置id，需要在这里判断一下重入，自行设置checkTableField方法会进行处理
                if (idFieldInformation.getId().saveField()) {
                    document.put(idFieldInformation.getName(), idValue);
                }
            }
        }
        //如果存在元对象处理器，且插入或更新字段不为空，则获取自动填充字段
        if (HandlerCache.metaObjectHandler != null && !insertFillAutoFillMetaObject.isEmpty()){
            insertFillAutoFillMetaObject.setTargetObject(typeInformation);
            HandlerCache.metaObjectHandler.insertFill(insertFillAutoFillMetaObject);
        }
        //映射到Document
        write(sourceObj,document);
        //添加自动填充字段
        insertFillAutoFillMetaObject.getAllFillFieldAndClear(document);
    }

    @Override
    public void writeByUpdate(Object sourceObj, Document document) {
        // Map类型不需要再做下边的操作 因为它们只针对实体类
        if (ClassTypeUtil.isTargetClass(Map.class,sourceObj.getClass())){
            write((Map<?,?>) sourceObj,document);
            return;
        }
        //封装class信息
        TypeInformation typeInformation = TypeInformation.of(sourceObj);
        //如果存在元对象处理器，且插入或更新字段为空，则获取自动填充字段
        if (HandlerCache.metaObjectHandler != null && updateFillAutoFillMetaObject.isEmpty()){
            //获取所有自动填充数据
            getFillInsertAndUpdateField(typeInformation,insertFillAutoFillMetaObject,updateFillAutoFillMetaObject);
        }
        //拿到类中的@ID字段
        FieldInformation idFieldInformation = typeInformation.getAnnotationField(ID.class, "@ID field not found");
        if (idFieldInformation.getValue() != null) {
            document.put(SqlOperationConstant._ID, idFieldInformation.getValue());
        }
        //如果存在元对象处理器，且插入或更新字段不为空，则获取自动填充字段
        if (HandlerCache.metaObjectHandler != null && !updateFillAutoFillMetaObject.isEmpty()){
            updateFillAutoFillMetaObject.setTargetObject(typeInformation);
            HandlerCache.metaObjectHandler.updateFill(updateFillAutoFillMetaObject);
        }
        //映射到Document
        write(sourceObj,document);
        //添加自动填充字段
        updateFillAutoFillMetaObject.getAllFillFieldAndClear(document);
    }

    public void getFillInsertAndUpdateField(TypeInformation typeInformation, AutoFillMetaObject insertFillAutoFillMetaObject, AutoFillMetaObject updateFillAutoFillMetaObject){
        typeInformation.getFields().forEach(field -> {
            CollectionField collectionField = field.getCollectionField();
            if (collectionField != null && collectionField.fill() != FieldFill.DEFAULT){
                MongoPlusDocument insertFillAutoField = insertFillAutoFillMetaObject.getDocument();
                MongoPlusDocument updateFillAutoField = updateFillAutoFillMetaObject.getDocument();
                if (collectionField.fill() == FieldFill.INSERT){
                    insertFillAutoField.put(field.getName(),field.getValue());
                }
                if (collectionField.fill() == FieldFill.UPDATE){
                    updateFillAutoField.put(field.getName(),field.getValue());
                }
                if (collectionField.fill() == FieldFill.INSERT_UPDATE){
                    insertFillAutoField.put(field.getName(),field.getValue());
                    updateFillAutoField.put(field.getName(),field.getValue());
                }
            }
        });
    }

    @Override
    public void write(Object sourceObj, Bson bson) {
        if (null == sourceObj) {
            return;
        }
        //如果为空，则创建一个
        bson = bson != null ? bson : new Document();
        if (ClassTypeUtil.isTargetClass(Map.class,sourceObj.getClass())){
            write((Map<?,?>) sourceObj,bson);
        } else {
            write(sourceObj, bson, TypeInformation.of(sourceObj));
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> T readInternal(Document document, TypeReference<T> typeReference, boolean useIdAsFieldName) {
        Class<?> clazz = typeReference.getClazz();
        if (document == null) {
            return null;
        }
        if (ClassTypeUtil.isTargetClass(Document.class,clazz)) {
            return (T) document;
        } else if (ClassTypeUtil.isTargetClass(Map.class,clazz)) {
            return (T) readInternal(document, new TypeReference<Map<String, Object>>() {});
        } else if (ClassTypeUtil.isTargetClass(Collection.class,clazz)){
            return (T) readInternal(document, new TypeReference<Collection<Object>>() {});
        }
        // 拿到class封装类
        TypeInformation typeInformation = TypeInformation.of(clazz);

        // 循环所有字段
        typeInformation.getFields().forEach(fieldInformation -> {
            String fieldName = useIdAsFieldName ? fieldInformation.getIdOrCamelCaseName() : fieldInformation.getCamelCaseName();
            if (fieldInformation.isSkipCheckField()) {
                return;
            }
            Object obj = document.get(fieldName);
            if (obj == null) {
                return;
            }
            CollectionField collectionField = fieldInformation.getCollectionField();
            Object resultObj = null;
            if (collectionField != null && ClassTypeUtil.isTargetClass(TypeHandler.class,collectionField.typeHandler())){
                TypeHandler typeHandler = (TypeHandler) ClassTypeUtil.getInstanceByClass(collectionField.typeHandler());
                resultObj = typeHandler.getResult(obj);
            }
            if (CollUtil.isNotEmpty(HandlerCache.readHandlerList)) {
                List<ReadHandler> readHandlerList = HandlerCache.readHandlerList.stream().sorted(Comparator.comparingInt(ReadHandler::order)).collect(Collectors.toList());
                for (ReadHandler readHandler : readHandlerList) {
                    obj = readHandler.read(fieldInformation, obj);
                }
            }
            if (resultObj == null) {
                resultObj = readInternal(obj, TypeReference.of(fieldInformation.getGenericType()));
            }
            fieldInformation.setValue(resultObj);
        });

        return typeInformation.getInstance();
    }

    /**
     * 抽象的映射方法
     * @param sourceObj        映射源对象
     * @param bson             映射对象
     * @param typeInformation 类信息
     * @author anwen
     * @date 2024/5/1 下午6:40
     */
    public abstract void write(Object sourceObj, Bson bson, TypeInformation typeInformation);

    /**
     * 抽象的map写入方法
     * @param obj map
     * @param bson bson
     * @return {@link org.bson.conversions.Bson}
     * @author anwen
     * @date 2024/6/26 下午2:23
     */
    public abstract Bson writeMapInternal(Map<?,?> obj,Bson bson);

    /**
     * 生成id，写在这里，方便自己自定义
     * @param idTypeEnum id枚举类型
     * @param typeInformation 类信息
     * @return {@link Serializable}
     * @author anwen
     * @date 2024/5/1 下午9:26
     */
    public Serializable generateId(IdTypeEnum idTypeEnum, TypeInformation typeInformation){
        if (idTypeEnum.getKey() == IdTypeEnum.ASSIGN_UUID.getKey()){
            return IdWorker.get32UUID();
        }
        if (idTypeEnum.getKey() == IdTypeEnum.ASSIGN_ULID.getKey()){
            return IdWorker.get26ULID();
        }
        if (idTypeEnum.getKey() == IdTypeEnum.ASSIGN_ID.getKey()){
            return IdWorker.getId();
        }
        if (idTypeEnum.getKey() == IdTypeEnum.AUTO.getKey()){
            return generateAutoId(typeInformation);
        }
        if (idTypeEnum.getKey() == IdTypeEnum.OBJECT_ID.getKey()){
            return new ObjectId();
        }
        return null;
    }

    /**
     * 生成自增id，写在这里，方便自定义
     * @param typeInformation 类信息
     * @return {@link Integer}
     * @author anwen
     * @date 2024/5/1 下午9:26
     */
    public Integer generateAutoId(TypeInformation typeInformation){
        String collectionName = AnnotationOperate.getCollectionName(typeInformation.getClazz());
        // 每个Collection单独加锁
        synchronized (collectionName.intern()) {
            MongoCollection<Document> collection = mongoPlusClient.getCollection(typeInformation.getClazz(), PropertyCache.autoIdCollectionName);
            Document query = new Document(SqlOperationConstant._ID, collectionName);
            Document update = new Document("$inc", new Document(SqlOperationConstant.AUTO_NUM, 1));
            Document document = Optional.ofNullable(MongoTransactionContext.getClientSessionContext())
                    .map(session -> collection.findOneAndUpdate(session, query, update, new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)))
                    .orElseGet(() -> collection.findOneAndUpdate(query, update, new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)));
            int finalNum = 1;
            if (document == null) {
                Map<String, Object> map = new HashMap<>();
                map.put(SqlOperationConstant._ID, collectionName);
                map.put(SqlOperationConstant.AUTO_NUM, finalNum);
                collection.insertOne(new Document(map));
            } else {
                finalNum = Integer.parseInt(String.valueOf(document.get(SqlOperationConstant.AUTO_NUM)));
            }
            return finalNum;
        }
    }

    /**
     * 将简单类型进行转换
     * @param value 值
     * @return {@link Object}
     * @author anwen
     * @date 2024/5/1 下午9:28
     */
    protected Object getPotentiallyConvertedSimpleWrite(Object value) {

        if (value == null) {
            return null;
        }

        if (CollUtil.isArray(value)) {

            if (value instanceof byte[]) {
                return value;
            }
            return BsonUtil.asCollection(value);
        }

        return ClassTypeUtil.isTargetClass(Enum.class, value.getClass()) ? ((Enum<?>) value).name() : value;
    }

    /**
     * 调用该方法，肯定会走集合和map之外的转换器
     * @param obj 值
     * @param clazz 类型
     * @return {@link T}
     * @author anwen
     * @date 2024/5/7 下午4:05
     */
    @SuppressWarnings("unchecked")
    protected <T> T convertValue(Object obj,Class<?> clazz){
        ConversionStrategy<?> conversionStrategy = getConversionStrategy(clazz);
        if (conversionStrategy == null){
            conversionStrategy = ConversionCache.getConversionStrategy(Object.class);
        }
        try {
            return (T) conversionStrategy.convertValue(obj,clazz,this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ConversionStrategy<?> getConversionStrategy(Class<?> target){
        // 使用 computeIfAbsent 来减少重复计算
        Boolean isEnumType = classEnumTypeMap.computeIfAbsent(target, key -> ClassTypeUtil.isTargetClass(Enum.class, key));

        if (isEnumType) {
            target = Enum.class;
        }

        // 获取并返回转换策略
        return ConversionCache.getConversionStrategy(target);
    }

    @SuppressWarnings("unchecked")
    protected MappingStrategy<Object> getMappingStrategy(Class<?> target){
        return (MappingStrategy<Object>) MappingCache.getMappingStrategy(target);
    }

}
