package com.mongoplus.strategy.executor.impl;

import com.mongoplus.enums.ExecuteMethodEnum;
import com.mongoplus.interceptor.Interceptor;
import com.mongoplus.model.MutablePair;
import com.mongoplus.strategy.executor.MethodExecutorStrategy;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;

/**
 * 修改 策略执行器
 *
 * @author anwen
 */
public class UpdateExecutorStrategy implements MethodExecutorStrategy {

    @Override
    public ExecuteMethodEnum method() {
        return ExecuteMethodEnum.UPDATE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Interceptor interceptor, Object[] args) {
        List<MutablePair<Bson, Bson>> bsonBsonMutablePairList = interceptor.executeUpdate((List<MutablePair<Bson,Bson>>) args[0]);
        args[0] = bsonBsonMutablePairList;
        bsonBsonMutablePairList = interceptor.executeUpdate((List<MutablePair<Bson,Bson>>) args[0], (MongoCollection<Document>) args[args.length-1]);
        args[0] = bsonBsonMutablePairList;
    }

}