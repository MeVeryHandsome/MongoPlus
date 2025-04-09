package com.mongoplus.annotation.transactional;

import com.mongoplus.enums.ReadPreferenceEnum;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 读取偏好设置
 *
 * @author loser
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MongoReadPreference {

    /**
     * 一种读取偏好设置，它指定所有的读操作都应该由哪个处理
     */
    ReadPreferenceEnum preferenceEnum() default ReadPreferenceEnum.PRIMARY;

    /**
     * maxStaleness选项是用来设置最大过时容忍度的。
     * 这个选项在配置复制集的读取偏好时非常有用，它允许客户端读取已经过时的（不同步的）副本集成员的数据。maxStaleness的值越大，客户端可以容忍的过时时间就越长
     */
    long maxStaleness() default 0;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

}
