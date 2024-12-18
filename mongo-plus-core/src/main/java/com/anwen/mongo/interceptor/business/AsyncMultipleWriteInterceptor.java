package com.anwen.mongo.interceptor.business;

import com.anwen.mongo.cache.global.DataSourceNameCache;
import com.anwen.mongo.domain.MongoPlusDsException;
import com.anwen.mongo.enums.ExecuteMethodEnum;
import com.anwen.mongo.enums.MultipleWrite;
import com.anwen.mongo.execute.instance.DefaultExecute;
import com.anwen.mongo.handlers.write.MultipleWriteHandler;
import com.anwen.mongo.interceptor.AdvancedInterceptor;
import com.anwen.mongo.interceptor.Invocation;
import com.anwen.mongo.logging.Log;
import com.anwen.mongo.logging.LogFactory;
import com.anwen.mongo.logic.LogicRemove;
import com.anwen.mongo.manager.MongoPlusClient;
import com.anwen.mongo.model.MutablePair;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.anwen.mongo.enums.MultipleWrite.*;

/**
 * 异步多写，并不能保证事务，通常用于数据备份
 * @author anwen
 */
@SuppressWarnings("unchecked")
public class AsyncMultipleWriteInterceptor implements AdvancedInterceptor {

    private final Log log = LogFactory.getLog(AsyncMultipleWriteInterceptor.class);

    private final ThreadPoolExecutor executor;

    protected MultipleWriteHandler multipleWriteHandler;

    private final MongoPlusClient mongoPlusClient;

    private DefaultExecute execute = new DefaultExecute();

    public void setExecute(DefaultExecute execute) {
        this.execute = execute;
    }

    public void setMultipleWriteHandler(MultipleWriteHandler multipleWriteHandler) {
        this.multipleWriteHandler = multipleWriteHandler;
    }

    public AsyncMultipleWriteInterceptor(MongoPlusClient mongoPlusClient) {
        this.executor = defaultExecutor();
        this.mongoPlusClient = mongoPlusClient;
        this.multipleWriteHandler = new MultipleWriteHandler(mongoPlusClient) {};
    }

    public AsyncMultipleWriteInterceptor(MongoPlusClient mongoPlusClient,MultipleWriteHandler multipleWriteHandler) {
        this.executor = defaultExecutor();
        this.mongoPlusClient = mongoPlusClient;
        this.multipleWriteHandler = multipleWriteHandler;
    }

    public AsyncMultipleWriteInterceptor(MongoPlusClient mongoPlusClient, ThreadPoolExecutor executor) {
        this.mongoPlusClient = mongoPlusClient;
        this.executor = executor;
        this.multipleWriteHandler = new MultipleWriteHandler(mongoPlusClient) {};
    }

    public AsyncMultipleWriteInterceptor(MongoPlusClient mongoPlusClient, ThreadPoolExecutor executor,
                                         MultipleWriteHandler multipleWriteHandler) {
        this.mongoPlusClient = mongoPlusClient;
        this.executor = executor;
        this.multipleWriteHandler = multipleWriteHandler;
    }

    final ThreadPoolExecutor defaultExecutor(){
        return new ThreadPoolExecutor(
                5, // 核心线程数
                20, // 最大线程数
                60L, // 空闲线程存活时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        ExecuteMethodEnum executeMethod = invocation.getExecuteMethod();
        Object[] source = invocation.getArgs();
        MongoCollection<Document> collection = invocation.getCollection();
        if (executeMethod == ExecuteMethodEnum.SAVE) {
            executeSave((List<Document>) source[0], (InsertManyOptions) source[1], collection);
        }
        if (executeMethod == ExecuteMethodEnum.REMOVE) {
            executeRemove((Bson) source[0], (DeleteOptions) source[1],invocation, collection);
        }
        if (executeMethod == ExecuteMethodEnum.UPDATE) {
            executeUpdate((List<MutablePair<Bson, Bson>>) source[0], (UpdateOptions) source[1], collection);
        }
        if (executeMethod == ExecuteMethodEnum.BULK_WRITE) {
            executeBulkWrite((List<WriteModel<Document>>) source[0],(BulkWriteOptions) source[1], collection);
        }
        return invocation.proceed();
    }

    void executeSave(List<Document> documentList, InsertManyOptions options, MongoCollection<Document> collection) {
        executeMultipleWrite(
                SAVE,
                collection,
                mongoCollection -> execute.executeSave(documentList,options, mongoCollection)
        );
    }

    void executeRemove(Bson filter, DeleteOptions options,Invocation invocation,
                              MongoCollection<Document> collection) {
        executeMultipleWrite(
                REMOVE,
                collection,
                mongoCollection -> {
                    try {
                        LogicRemove.logic(invocation,mongoCollection);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    void executeUpdate(List<MutablePair<Bson, Bson>> updatePairList,
                                                       UpdateOptions options,
                                                       MongoCollection<Document> collection) {
        executeMultipleWrite(
                UPDATE,
                collection,
                mongoCollection -> execute.executeUpdate(updatePairList,options, mongoCollection)
        );
    }

    void executeBulkWrite(List<WriteModel<Document>> writeModelList,
                                                       BulkWriteOptions options,
                                                       MongoCollection<Document> collection) {
        executeMultipleWrite(
                BULK_WRITE,
                collection,
                mongoCollection -> execute.executeBulkWrite(writeModelList,options, mongoCollection)
        );
    }

    void executeMultipleWrite(MultipleWrite multipleWrite,MongoCollection<Document> collection,
                                      Consumer<MongoCollection<Document>> action) {
        MongoNamespace namespace = collection.getNamespace();
        List<String> multipleWriteTargets = multipleWriteHandler.getMultipleWrite(multipleWrite, namespace);
        multipleWriteTargets.forEach(dsName -> executor.submit(() -> {
            if (!dsName.equals(DataSourceNameCache.getDataSource())) {
                MongoCollection<Document> mongoCollection = getMongoCollection(namespace, dsName);
                log.info("Executing multiple write operation on data source: " + dsName);
                action.accept(mongoCollection);
            }
        }));
    }

    MongoCollection<Document> getMongoCollection(MongoNamespace namespace, String dsName) {
        MongoClient mongoClient = mongoPlusClient.getMongoClient(dsName);
        if (mongoClient == null) {
            throw new MongoPlusDsException("Non-existent data source: " + dsName);
        }
        return mongoPlusClient.getCollection(dsName,namespace.getDatabaseName(),namespace.getCollectionName());
    }
}

