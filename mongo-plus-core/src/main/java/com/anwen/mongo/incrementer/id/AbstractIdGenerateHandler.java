package com.anwen.mongo.incrementer.id;

import com.anwen.mongo.cache.global.PropertyCache;
import com.anwen.mongo.constant.SqlOperationConstant;
import com.anwen.mongo.context.MongoTransactionContext;
import com.anwen.mongo.enums.IdTypeEnum;
import com.anwen.mongo.handlers.IdGenerateHandler;
import com.anwen.mongo.handlers.collection.AnnotationOperate;
import com.anwen.mongo.manager.MongoPlusClient;
import com.anwen.mongo.mapping.TypeInformation;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 抽象的id生成处理器
 *
 * @author anwen
 */
public abstract class AbstractIdGenerateHandler implements IdGenerateHandler {

    protected final MongoPlusClient mongoPlusClient;

    public MongoPlusClient getMongoPlusClient() {
        return mongoPlusClient;
    }

    public AbstractIdGenerateHandler(MongoPlusClient mongoPlusClient) {
        this.mongoPlusClient = mongoPlusClient;
    }

    @Override
    public Serializable generateId(IdTypeEnum idTypeEnum, TypeInformation typeInformation) {
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
     * 生成自增id
     * @param typeInformation 类信息
     * @return {@link java.lang.Integer}
     * @author anwen
     * @date 2024/9/24 00:28
     */
    public Integer generateAutoId(TypeInformation typeInformation) {
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

}
