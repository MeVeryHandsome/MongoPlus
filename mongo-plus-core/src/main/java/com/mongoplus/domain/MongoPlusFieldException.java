package com.mongoplus.domain;

/**
 * MongoPlus字段相关异常
 * @author JiaChaoYang
 * @date 2024/5/2 上午1:30
 */
public class MongoPlusFieldException extends MongoPlusException {

    public MongoPlusFieldException(String message) {
        super(message);
    }

    public MongoPlusFieldException(String message,Throwable cause) {
        super(message,cause);
    }
}
