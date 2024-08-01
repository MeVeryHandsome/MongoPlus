package com.anwen.mongo.property;

import com.anwen.mongo.cache.global.PropertyCache;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author JiaChaoYang
 **/
@ConfigurationProperties(prefix = "mongo-plus.configuration.field")
public class MongoDBFieldProperty {

    /**
     * 驼峰转下划线，将对象属性映射到数据库
     * 因为以前使用下划线转驼峰配置，存入数据库时不会转成下划线，只会查询时转为驼峰
     * 直接配置该配置，则实现映射到数据库会转下划线，映射回对象，则转驼峰
     * @author JiaChaoYang
     * @date 2023/10/12 0:09
     */
    private Boolean camelToUnderline = false;

    /**
     * 是否忽略null属性
     * @author JiaChaoYang
     * @date 2024/5/1 下午1:30
     */
    private Boolean ignoringNull = true;

    public Boolean getIgnoringNull() {
        return ignoringNull;
    }

    public void setIgnoringNull(Boolean ignoringNull) {
        PropertyCache.ignoringNull = ignoringNull;
        this.ignoringNull = ignoringNull;
    }

    public Boolean getCamelToUnderline() {
        return camelToUnderline;
    }

    public void setCamelToUnderline(Boolean camelToUnderline) {
        PropertyCache.camelToUnderline = camelToUnderline;
        this.camelToUnderline = camelToUnderline;
    }
}
