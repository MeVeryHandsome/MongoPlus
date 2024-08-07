package com.anwen.mongo.interceptor.business;

import com.anwen.mongo.cache.codec.MapCodecCache;
import com.anwen.mongo.cache.global.DataSourceNameCache;
import com.anwen.mongo.constant.DataSourceConstant;
import com.anwen.mongo.domain.MongoPlusException;
import com.anwen.mongo.enums.ExecuteMethodEnum;
import com.anwen.mongo.enums.SpecialConditionEnum;
import com.anwen.mongo.interceptor.Interceptor;
import com.anwen.mongo.logging.Log;
import com.anwen.mongo.logging.LogFactory;
import com.anwen.mongo.mapper.BaseMapper;
import com.anwen.mongo.model.MutablePair;
import com.anwen.mongo.model.OperationResult;
import com.anwen.mongo.toolkit.StringUtils;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.WriteModel;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 数据变动记录拦截器
 * <p>请不要与自增id同时使用</p>
 * @author anwen
 * @date 2024/6/27 下午5:01
 * @since by mybatis-plus
 */
@SuppressWarnings("unchecked")
public class DataChangeRecorderInnerInterceptor implements Interceptor {

    private final Log log = LogFactory.getLog(DataChangeRecorderInnerInterceptor.class);

    /**
     * 超出阈值提示信息
     *
     * @date 2024/6/27 下午5:48
     */
    private String exceptionMessage = "The operation has exceeded the security threshold and has been intercepted!";

    /**
     * 忽略的表
     *
     * @date 2024/6/27 下午5:33
     */
    private List<String> ignoredColumnList = new CopyOnWriteArrayList<String>(){{
        add("DATA_CHANGE_RECORD");
    }};

    /**
     * 批量更新条数上限
     *
     * @date 2024/6/27 下午5:32
     */
    private Integer batchUpdateLimit = 1000;

    /**
     * 是否显示完整数据，开启后，changedData字段数据量可能会很大
     * 默认不开启，只显示数量
     * @date 2024/6/27 下午8:14
     */
    private Boolean displayCompleteData = true;

    /**
     * 是否保存到数据库
     * @date 2024/7/26 下午8:47
     */
    private Boolean enableSaveDatabase = false;

    /**
     * baseMapper
     * @date 2024/7/26 下午8:39
     */
    private BaseMapper baseMapper;

    /**
     * 数据源，默认获取上下文中的数据源，推荐手动设置
     * @date 2024/7/26 下午8:40
     */
    private String datasourceName;

    /**
     * 是否使用默认数据源保存数据，如果设置了datasourceName，该配置会失效
     * <p>推荐设置，使用主数据源，避免连续切库</p>
     * @date 2024/7/26 下午8:45
     */
    private Boolean isMasterDatasource = false;

    /**
     * 数据库，默认获取上下文中对应的数据源的库
     * @date 2024/7/26 下午8:41
     */
    private String databaseName;

    /**
     * 集合名
     * @date 2024/7/26 下午8:46
     */
    private String collectionName = "DATA_CHANGE_RECORD";

    private static final ThreadLocal<OperationResult> operationResultThreadLocal = ThreadLocal.withInitial(() -> null);

    @Override
    public void beforeExecute(ExecuteMethodEnum executeMethodEnum, Object[] source, MongoCollection<Document> collection) {
        if (shouldIgnoreCollection(collection)) {
            return;
        }

        long startTs = System.currentTimeMillis();
        OperationResult operationResult = processOperation(executeMethodEnum, source);

        if (operationResult != null) {
            MongoNamespace namespace = collection.getNamespace();
            operationResult.setDatasourceName(DataSourceNameCache.getDataSource());
            operationResult.setDatabaseName(namespace.getDatabaseName());
            operationResult.setCollectionName(namespace.getCollectionName());
            operationResult.setRecordStatus(true);
            long costThis = System.currentTimeMillis() - startTs;
            operationResult.setCost(costThis);
            log.info(String.format("%s DataChangeRecord: %s",executeMethodEnum.name(), operationResult));
            if (enableSaveDatabase) {
                operationResultThreadLocal.set(operationResult);
            }
        }
    }

    @Override
    public void afterExecute(ExecuteMethodEnum executeMethodEnum, Object[] source, Object result, MongoCollection<Document> collection) {
        if (shouldIgnoreCollection(collection) || !isRelevantMethod(executeMethodEnum)) {
            return;
        }

        if (enableSaveDatabase) {
            String datasource = determineDatasource();
            DataSourceNameCache.setDataSource(datasource);
            String databaseName = determineDatabaseName();
            baseMapper.save(databaseName, collectionName, operationResultThreadLocal.get());
            operationResultThreadLocal.remove();
        }
    }

    private boolean shouldIgnoreCollection(MongoCollection<Document> collection) {
        return ignoredColumnList.contains(collection.getNamespace().getCollectionName());
    }

    private boolean isRelevantMethod(ExecuteMethodEnum executeMethodEnum) {
        return executeMethodEnum == ExecuteMethodEnum.SAVE ||
                executeMethodEnum == ExecuteMethodEnum.UPDATE ||
                executeMethodEnum == ExecuteMethodEnum.REMOVE ||
                executeMethodEnum == ExecuteMethodEnum.BULK_WRITE;
    }

    private String determineDatasource() {
        if (StringUtils.isNotBlank(this.datasourceName)) {
            return this.datasourceName;
        } else if (this.isMasterDatasource) {
            return DataSourceConstant.DEFAULT_DATASOURCE;
        }
        return DataSourceNameCache.getDataSource();
    }

    private String determineDatabaseName() {
        return StringUtils.isNotBlank(this.databaseName) ? this.databaseName : DataSourceNameCache.getDatabase();
    }

    private OperationResult processOperation(ExecuteMethodEnum executeMethodEnum, Object[] source) throws DataUpdateLimitationException {
        switch (executeMethodEnum) {
            case SAVE:
                return processSave(source);
            case UPDATE:
                return processUpdate(source);
            case REMOVE:
                return processRemove(source);
            case BULK_WRITE:
                return processBulkWrite(source);
            default:
                return null;
        }
    }

    private OperationResult processSave(Object[] source) throws DataUpdateLimitationException {
        List<Document> documentList = castList(source[0]);
        if (documentList.size() > batchUpdateLimit) {
            log.error("batch save limit exceed: count={}, BATCH_UPDATE_LIMIT={}", documentList.size(), batchUpdateLimit);
            throw new DataUpdateLimitationException(exceptionMessage);
        }
        OperationResult operationResult = new OperationResult();
        operationResult.setOperation(ExecuteMethodEnum.SAVE.name());
        operationResult.setChangedData(displayCompleteData ? documentList.toString() : String.valueOf(documentList.size()));
        return operationResult;
    }

    private OperationResult processUpdate(Object[] source) throws DataUpdateLimitationException {
        List<MutablePair<Bson, Bson>> documentList = castList(source[0]);
        if (documentList.size() > batchUpdateLimit) {
            log.error("batch update limit exceed: count={}, BATCH_UPDATE_LIMIT={}", documentList.size(), batchUpdateLimit);
            throw new DataUpdateLimitationException(exceptionMessage);
        }
        OperationResult operationResult = new OperationResult();
        operationResult.setOperation(ExecuteMethodEnum.UPDATE.name());
        List<String> dataList = documentList.stream()
                .map(mutablePair -> {
                    String left = mutablePair.getRight().toBsonDocument(BsonDocument.class, MapCodecCache.getDefaultCodecRegistry()).toString();
                    String right = mutablePair.getRight().toBsonDocument(BsonDocument.class, MapCodecCache.getDefaultCodecRegistry()).toString();
                    return "(left=" + left + ",right=" + right + ")";
                })
                .collect(Collectors.toList());
        operationResult.setChangedData(displayCompleteData ? dataList.toString() : String.valueOf(documentList.size()));
        return operationResult;
    }

    private OperationResult processRemove(Object[] source) throws DataUpdateLimitationException {
        Bson bson = (Bson) source[0];
        BsonDocument bsonDocument = bson.toBsonDocument(BsonDocument.class, MapCodecCache.getDefaultCodecRegistry());
        bsonDocument.forEach((k, v) -> {
            if (v.isDocument() && v.asDocument().containsKey(SpecialConditionEnum.IN.getCondition())) {
                BsonArray inArray = v.asDocument().get(SpecialConditionEnum.IN.getCondition()).asArray();
                if (inArray.size() > batchUpdateLimit) {
                    log.error("batch remove limit exceed: count={}, BATCH_UPDATE_LIMIT={}", inArray.size(), batchUpdateLimit);
                    throw new DataUpdateLimitationException(exceptionMessage);
                }
            }
        });
        OperationResult operationResult = new OperationResult();
        operationResult.setOperation(ExecuteMethodEnum.REMOVE.name());
        operationResult.setChangedData(displayCompleteData ? bsonDocument.toString() : String.valueOf(bsonDocument.size()));
        return operationResult;
    }

    private OperationResult processBulkWrite(Object[] source) {
        List<WriteModel<Document>> writeModelList = castList(source[0]);
        long insertCount = writeModelList.stream().filter(writeModel -> writeModel instanceof InsertOneModel).count();
        long updateCount = writeModelList.stream().filter(writeModel -> writeModel instanceof UpdateManyModel).count();
        if (insertCount > batchUpdateLimit || updateCount > batchUpdateLimit) {
            log.error("batch bulkWrite limit exceed: count={}, BATCH_UPDATE_LIMIT={}", insertCount, batchUpdateLimit);
            throw new DataUpdateLimitationException(exceptionMessage);
        }
        OperationResult operationResult = new OperationResult();
        operationResult.setOperation(ExecuteMethodEnum.BULK_WRITE.name());
        if (displayCompleteData) {
            List<String> dataList = writeModelList.stream()
                    .map(writeModel -> {
                        if (writeModel instanceof InsertOneModel) {
                            return writeModel.toString();
                        } else if (writeModel instanceof UpdateManyModel) {
                            UpdateManyModel<Document> updateManyModel = (UpdateManyModel<Document>) writeModel;
                            return "UpdateManyModel{filter=" + updateManyModel.getFilter() +
                                    ", update=" + (updateManyModel.getUpdate() != null ? updateManyModel.getUpdate().toBsonDocument(BsonDocument.class, MapCodecCache.getDefaultCodecRegistry()).toString() : updateManyModel.getUpdatePipeline()) +
                                    ", options=" + updateManyModel.getUpdatePipeline() + '}';
                        }
                        return "";
                    })
                    .collect(Collectors.toList());
            operationResult.setChangedData(dataList.toString());
        } else {
            operationResult.setChangedData(String.valueOf(writeModelList.size()));
        }
        return operationResult;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(Object obj) {
        return (List<T>) obj;
    }

    public String getDatasourceName() {
        return datasourceName;
    }

    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }

    public Boolean getIsMasterDatasource() {
        return isMasterDatasource;
    }

    public void isMasterDatasource(Boolean masterDatasource) {
        isMasterDatasource = masterDatasource;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.ignoredColumnList.add(collectionName);
        this.collectionName = collectionName;
    }

    public void enableSaveDatabase(BaseMapper baseMapper){
        this.enableSaveDatabase = true;
        this.baseMapper = baseMapper;
    }

    public BaseMapper getBaseMapper() {
        return baseMapper;
    }

    public Boolean getDisplayCompleteData() {
        return displayCompleteData;
    }

    public void setDisplayCompleteData(Boolean displayCompleteData) {
        this.displayCompleteData = displayCompleteData;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public List<String> getIgnoredColumnList() {
        return ignoredColumnList;
    }

    public void setIgnoredColumnList(List<String> ignoredColumnList) {
        this.ignoredColumnList = ignoredColumnList;
    }

    public Integer getBatchUpdateLimit() {
        return batchUpdateLimit;
    }

    public void setBatchUpdateLimit(Integer batchUpdateLimit) {
        this.batchUpdateLimit = batchUpdateLimit;
    }

    public static class DataUpdateLimitationException extends MongoPlusException {

        public DataUpdateLimitationException(String message) {
            super(message);
        }

    }

}
