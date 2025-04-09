package com.mongoplus.toolkit;

import com.mongoplus.domain.MongoPlusConvertException;
import com.mongoplus.logging.Log;
import com.mongoplus.logging.LogFactory;

import java.time.*;

/**
 * 时间戳工具
 *
 * @author JiaChaoYang
 **/
public class InstantUtil {

    private static final Log log = LogFactory.getLog(InstantUtil.class);

    /**
     * 时间戳转LocalDateTime
     * @param timestamp 时间戳
     * @author JiaChaoYang
    */
    public static LocalDateTime convertTimestampToLocalDateTime8(long timestamp) {
        return convertTimestampToLocalDateTime(timestamp).minusHours(8);
    }

    public static LocalDateTime convertTimestampToLocalDateTime(long timestamp){
        Instant instant;
        try {
            instant = Instant.ofEpochMilli(timestamp);
        }catch (Exception e){
            log.error("Convert To Instant Fail,message: {}",e.getMessage(),e);
            throw new MongoPlusConvertException("Convert To Instant Fail");
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public static LocalDateTime convertTimestampToLocalDateTime8(Instant instant){
        return LocalDateTime.ofInstant(instant,ZoneId.systemDefault()).minusHours(8);
    }

    /**
     * 时间戳转LocalDate
     * 不使用LocalDate的ofInstant的方法，因为java8不可用，这样做可以兼容版本
     * @param timestamp 时间戳
     * @author JiaChaoYang
    */
    public static LocalDate convertTimestampToLocalDate(long timestamp){
        return convertTimestampToLocalDateTime8(timestamp).toLocalDate();
    }

    public static LocalDate convertTimestampToLocalDate(Instant instant){
        return convertTimestampToLocalDateTime8(instant).toLocalDate();
    }

    /**
     * 时间戳转LocalDate
     * 不使用LocalDate的ofInstant的方法，因为java8不可用，这样做可以兼容版本
     * @param timestamp 时间戳
     * @author JiaChaoYang
    */
    public static LocalTime convertTimestampToLocalTime(long timestamp){
        return convertTimestampToLocalDateTime(timestamp).toLocalTime();
    }

    public static LocalTime convertTimestampToLocalTime(Instant instant){
        return convertTimestampToLocalDateTime8(instant).toLocalTime();
    }

}
