package com.anwen.mongo.strategy.aggregate.impl;

import com.anwen.mongo.strategy.aggregate.PipelineStrategy;
import com.anwen.mongo.toolkit.StringUtils;
import com.mongodb.BasicDBObject;

/**
 * out策略实现类
 *
 * @author JiaChaoYang
 **/
public class OutConcretePipeline implements PipelineStrategy {

    private final String db;

    private final String coll;

    public OutConcretePipeline(String db, String coll) {
        this.db = db;
        this.coll = coll;
    }

    @Override
    public BasicDBObject buildAggregate() {
        return new BasicDBObject(){{
            if (StringUtils.isNotBlank(db)){
                put("db",db);
                put("coll",coll);
            }
        }};
    }
}
