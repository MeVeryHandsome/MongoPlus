package com.anwen.mongo.strategy.executor.impl;

import com.anwen.mongo.enums.ExecuteMethodEnum;
import com.anwen.mongo.interceptor.Interceptor;
import com.anwen.mongo.strategy.executor.MethodExecutorStrategy;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

/**
 * 不接受任何参数的统计
 *
 * @author anwen
 */
public class EstimatedDocumentCountStrategy implements MethodExecutorStrategy {
    @Override
    public ExecuteMethodEnum method() {
        return ExecuteMethodEnum.ESTIMATED_DOCUMENT_COUNT;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Interceptor interceptor, Object[] args) {
        interceptor.executeEstimatedDocumentCount((MongoCollection<Document>) args[0]);
    }
}
