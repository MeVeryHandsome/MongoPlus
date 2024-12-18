package com.mongoplus.domain;

import com.mongoplus.cache.global.PropertyCache;
import com.mongoplus.toolkit.IkunRandomUtil;

/**
 * MongoPlus异常
 *
 * @author JiaChaoYang
 **/
public class MongoPlusException extends RuntimeException {

    String message;

    public MongoPlusException(String message) {
        super(PropertyCache.ikun ? IkunRandomUtil.getRandomThreadLog()+message : message);
        this.message = PropertyCache.ikun ? IkunRandomUtil.getRandomThreadLog()+message : message;
    }

    public MongoPlusException(String message, Throwable cause) {
        super(PropertyCache.ikun ? IkunRandomUtil.getRandomThreadLog()+message : message, cause);
    }

    public MongoPlusException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getMessage() {
        return message;
    }

}
