package com.mongoplus.handlers.condition;

import com.mongoplus.bson.MongoPlusBasicDBObject;
import com.mongoplus.cache.codec.MapCodecCache;
import com.mongoplus.cache.global.HandlerCache;
import com.mongoplus.conditions.AbstractChainWrapper;
import com.mongoplus.conditions.interfaces.TextSearchOptions;
import com.mongoplus.conditions.interfaces.condition.CompareCondition;
import com.mongoplus.conditions.interfaces.condition.Order;
import com.mongoplus.conditions.query.QueryChainWrapper;
import com.mongoplus.domain.MongoPlusException;
import com.mongoplus.enums.*;
import com.mongoplus.model.BaseConditionResult;
import com.mongoplus.model.BuildUpdate;
import com.mongoplus.model.MutablePair;
import com.mongoplus.toolkit.CollUtil;
import com.mongoplus.toolkit.Filters;
import com.mongodb.BasicDBObject;
import org.bson.BsonDocument;
import org.bson.BsonType;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.mongoplus.enums.QueryOperatorEnum.*;


/**
 * 构建条件
 *
 * @author anwen
 * @date 2024/6/30 下午4:07
 */
public class BuildCondition extends AbstractCondition {

    private static Condition DEFAULT_BUSINESS_CONDITION;

    public static Condition condition() {
        return DEFAULT_BUSINESS_CONDITION;
    }

    public static void setCondition(Condition condition) {
        DEFAULT_BUSINESS_CONDITION = condition;
    }

    static {
        DEFAULT_BUSINESS_CONDITION = new BuildCondition();
    }

    public BuildCondition() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public BasicDBObject queryCondition(CompareCondition compareCondition,
                                        MongoPlusBasicDBObject mongoPlusBasicDBObject) {
        HandlerCache.conditionHandlerList.forEach(conditionHandler ->
                conditionHandler.beforeQueryCondition(compareCondition, mongoPlusBasicDBObject));
        QueryOperatorEnum query = QueryOperatorEnum.getQueryOperator(compareCondition.getCondition());
        switch (Objects.requireNonNull(query)) {
            case EQ:
                mongoPlusBasicDBObject.put(
                        new Document(
                                compareCondition.getColumn(),
                                new Document(EQ.getOperatorValue(), compareCondition.getValue())
                        )
                );
                break;
            case NE:
                mongoPlusBasicDBObject.put(Filters.ne(compareCondition.getColumn(), compareCondition.getValue()));
                break;
            case GT:
                mongoPlusBasicDBObject.put(Filters.gt(compareCondition.getColumn(), compareCondition.getValue()));
                break;
            case LT:
                mongoPlusBasicDBObject.put(Filters.lt(compareCondition.getColumn(), compareCondition.getValue()));
                break;
            case GTE:
                mongoPlusBasicDBObject.put(Filters.gte(compareCondition.getColumn(), compareCondition.getValue()));
                break;
            case LTE:
                mongoPlusBasicDBObject.put(Filters.lte(compareCondition.getColumn(), compareCondition.getValue()));
                break;
            case REGEX:
            case LIKE:
                Document likeDocument = new Document(compareCondition.getColumn(),
                        new Document(REGEX.getOperatorValue(), compareCondition.getValue().toString())
                                .append(CommonOperators.OPTIONS.getOperator(), "i")
                );
                mongoPlusBasicDBObject.put(likeDocument);
                break;
            case IN:
                mongoPlusBasicDBObject.put(Filters.in(compareCondition.getColumn(),
                        (Collection<?>) compareCondition.getValue()));
                break;
            case NIN:
                mongoPlusBasicDBObject.put(Filters.nin(compareCondition.getColumn(),
                        (Collection<?>) compareCondition.getValue()));
                break;
            case AND:
                logic((QueryChainWrapper<?, ?>) compareCondition.getValue(), mongoPlusBasicDBObject, Filters::and);
                break;
            case OR:
                logic((QueryChainWrapper<?, ?>) compareCondition.getValue(), mongoPlusBasicDBObject, Filters::or);
                break;
            case NOR:
                logic((QueryChainWrapper<?, ?>) compareCondition.getValue(), mongoPlusBasicDBObject, Filters::nor);
                break;
            case TYPE:
                Object typeValue = compareCondition.getValue();
                if (typeValue instanceof String) {
                    mongoPlusBasicDBObject.put(Filters.type(compareCondition.getColumn(), (String) typeValue));
                    break;
                }
                if (typeValue instanceof TypeEnum) {
                    typeValue = ((TypeEnum) typeValue).getTypeCode();
                }
                mongoPlusBasicDBObject.put(Filters.type(compareCondition.getColumn(),
                        BsonType.findByValue((Integer) typeValue)));
                break;
            case EXISTS:
                mongoPlusBasicDBObject.put(Filters.exists(compareCondition.getColumn(),
                        (Boolean) compareCondition.getValue()));
                break;
            case NOT:
            case EXPR:
                QueryChainWrapper<?, ?> exprWrapper = (QueryChainWrapper<?, ?>) compareCondition.getValue();
                BaseConditionResult baseConditionResult = exprWrapper.buildCondition();
                BasicDBObject exprBasicDBObject = baseConditionResult.getCondition();
                Optional<String> exprOptional = exprBasicDBObject.keySet().stream().findFirst();
                if (exprOptional.isPresent()) {
                    String exprKey = exprOptional.get();
                    mongoPlusBasicDBObject.put(Filters.expr(new BasicDBObject(exprKey, exprBasicDBObject.get(exprKey))));
                }
                break;
            case MOD:
                List<Long> modList = (List<Long>) compareCondition.getValue();
                if (modList.size() < 2) {
                    throw new MongoPlusException("Mod requires modulus and remainder");
                }
                mongoPlusBasicDBObject.put(Filters.mod(compareCondition.getColumn(), modList.get(0), modList.get(1)));
                break;
            case ELEM_MATCH:
                QueryChainWrapper<?, ?> elemMatchWrapper = (QueryChainWrapper<?, ?>) compareCondition.getValue();
                BasicDBObject elemMatchBasicDBObject = queryCondition(elemMatchWrapper).getCondition();
                Bson elemMatchBson = Filters.elemMatch(compareCondition.getColumn(), elemMatchBasicDBObject);
                if (CollUtil.isNotEmpty(elemMatchWrapper.getBasicDBObjectList())) {
                    elemMatchWrapper.getBasicDBObjectList().forEach(bson ->
                            elemMatchBson.toBsonDocument(
                                            BsonDocument.class,
                                            MapCodecCache.getDefaultCodecRegistry()).
                                    putAll(bson.toBsonDocument(
                                            BsonDocument.class,
                                            MapCodecCache.getDefaultCodecRegistry()
                                    )));
                }
                mongoPlusBasicDBObject.put(elemMatchBson);
                break;
            case ALL:
                mongoPlusBasicDBObject.put(Filters.all(compareCondition.getColumn(),
                        (Collection<?>) compareCondition.getValue()));
                break;
            case TEXT:
                Bson textBson;
                Object value = compareCondition.getValue();
                TextSearchOptions textSearchOptions = compareCondition.getExtraValue(TextSearchOptions.class);
                if (textSearchOptions != null) {
                    textBson = Filters.text(value.toString(), textSearchOptions.to());
                } else {
                    textBson = Filters.text(value.toString());
                }
                mongoPlusBasicDBObject.put(textBson);
                break;
            case WHERE:
                mongoPlusBasicDBObject.put(Filters.where((String) compareCondition.getValue()));
                break;
            case SIZE:
                mongoPlusBasicDBObject.put(Filters.size(compareCondition.getColumn(),
                        (Integer) compareCondition.getValue()));
                break;
            case BITS_ALL_CLEAR:
                mongoPlusBasicDBObject.put(Filters.bitsAllClear(compareCondition.getColumn(),
                        (Integer) compareCondition.getValue()));
                break;
            case BITS_ALL_SET:
                mongoPlusBasicDBObject.put(Filters.bitsAllSet(compareCondition.getColumn(),
                        (Integer) compareCondition.getValue()));
                break;
            case BITS_ANY_CLEAR:
                mongoPlusBasicDBObject.put(Filters.bitsAnyClear(compareCondition.getColumn(),
                        (Integer) compareCondition.getValue()));
                break;
            case BITS_ANY_SET:
                mongoPlusBasicDBObject.put(Filters.bitsAnySet(compareCondition.getColumn(),
                        (Integer) compareCondition.getValue()));
                break;
        }
        HandlerCache.conditionHandlerList.forEach(conditionHandler ->
                conditionHandler.afterQueryCondition(compareCondition, mongoPlusBasicDBObject));
        return mongoPlusBasicDBObject;
    }

    @Override
    public BasicDBObject buildUpdateCondition(List<CompareCondition> compareConditionList, BuildUpdate buildUpdate) {
        CompareCondition currentCompareCondition = buildUpdate.getCurrentCompareCondition();
        BasicDBObject updateBasicDBObject = buildUpdate.getUpdateBasicDBObject();
        updateBasicDBObject.put(currentCompareCondition.getColumn(), currentCompareCondition.getValue());
        return updateBasicDBObject;
    }

    @Override
    public BasicDBObject buildPushCondition(List<CompareCondition> compareConditionList, BuildUpdate buildUpdate) {
        CompareCondition currentCompareCondition = buildUpdate.getCurrentCompareCondition();
        BasicDBObject updateBasicDBObject = buildUpdate.getUpdateBasicDBObject();
        List<Object> valueList = compareConditionList.stream()
                .filter(condition ->
                        Objects.equals(condition.getColumn(), currentCompareCondition.getColumn()))
                .map(CompareCondition::getValue)
                .collect(Collectors.toList());
        updateBasicDBObject.put(
                currentCompareCondition.getColumn(),
                new BasicDBObject(SpecialConditionEnum.EACH.getCondition(), valueList)
        );
        return updateBasicDBObject;
    }

    @Override
    public BasicDBObject buildCurrentDateCondition(List<CompareCondition> compareConditionList, BuildUpdate buildUpdate) {
        CompareCondition currentCompareCondition = buildUpdate.getCurrentCompareCondition();
        BasicDBObject updateBasicDBObject = buildUpdate.getUpdateBasicDBObject();
        CurrentDateType currentDateType = currentCompareCondition.getValue(CurrentDateType.class);
        updateBasicDBObject.put(currentCompareCondition.getColumn(),
                new BasicDBObject(SpecialConditionEnum.TYPE.getCondition(), currentDateType.getType()));
        return updateBasicDBObject;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BasicDBObject buildRenameCondition(List<CompareCondition> compareConditionList, BuildUpdate buildUpdate) {
        CompareCondition currentCompareCondition = buildUpdate.getCurrentCompareCondition();
        BasicDBObject updateBasicDBObject = buildUpdate.getUpdateBasicDBObject();
        MutablePair<String, String> pairValue = currentCompareCondition.getValue(MutablePair.class);
        updateBasicDBObject.put(pairValue.getLeft(), pairValue.getRight());
        return updateBasicDBObject;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BasicDBObject buildUnsetCondition(List<CompareCondition> compareConditionList, BuildUpdate buildUpdate) {
        CompareCondition currentCompareCondition = buildUpdate.getCurrentCompareCondition();
        BasicDBObject updateBasicDBObject = buildUpdate.getUpdateBasicDBObject();
        List<String> pairValue = currentCompareCondition.getValue(List.class);
        pairValue.forEach(column -> updateBasicDBObject.put(column, ""));
        return updateBasicDBObject;
    }

    @Override
    public BasicDBObject buildAddToSetCondition(List<CompareCondition> compareConditionList, BuildUpdate buildUpdate) {
        CompareCondition currentCompareCondition = buildUpdate.getCurrentCompareCondition();
        BasicDBObject updateBasicDBObject = buildUpdate.getUpdateBasicDBObject();
        updateBasicDBObject.put(currentCompareCondition.getColumn(),
                currentCompareCondition.getExtraValue(Boolean.class) ?
                        new BasicDBObject(SpecialConditionEnum.EACH.getCondition(), currentCompareCondition.getValue()) :
                        currentCompareCondition.getValue());
        return updateBasicDBObject;
    }

    @Override
    public BasicDBObject buildPullCondition(List<CompareCondition> compareConditionList, BuildUpdate buildUpdate) {
        CompareCondition currentCompareCondition = buildUpdate.getCurrentCompareCondition();
        BasicDBObject updateBasicDBObject = buildUpdate.getUpdateBasicDBObject();
        if (currentCompareCondition.getExtraValue(Boolean.class)) {
            QueryChainWrapper<?, ?> wrapper = currentCompareCondition.getValue(QueryChainWrapper.class);
            BasicDBObject queriedCondition = queryCondition(wrapper).getCondition();
            if (CollUtil.isNotEmpty(wrapper.getBasicDBObjectList())) {
                wrapper.getBasicDBObjectList().forEach(basicDBObject -> queriedCondition.putAll(
                        basicDBObject.toBsonDocument(BsonDocument.class, MapCodecCache.getDefaultCodecRegistry())
                ));
            }
            updateBasicDBObject.putAll(queriedCondition.toBsonDocument(
                    BsonDocument.class,
                    MapCodecCache.getDefaultCodecRegistry()
            ));
        } else {
            updateBasicDBObject.put(currentCompareCondition.getColumn(), currentCompareCondition.getValue());
        }
        return updateBasicDBObject;
    }

    @Override
    public BaseConditionResult queryCondition(AbstractChainWrapper<?, ?> wrapper) {
        List<BasicDBObject> basicDBObjectList = wrapper.getBasicDBObjectList();
        List<Order> orderList = wrapper.getOrderList();
        BasicDBObject sortCond = new BasicDBObject();
        if (CollUtil.isNotEmpty(orderList)) {
            orderList.forEach(order -> sortCond.put(order.getColumn(), order.getType()));
        }
        BasicDBObject basicDBObject = queryCondition(wrapper.getCompareList());
        if (CollUtil.isNotEmpty(basicDBObjectList)) {
            basicDBObjectList.forEach(basic -> basicDBObject.putAll(basic.toBsonDocument(
                    BsonDocument.class,
                    MapCodecCache.getDefaultCodecRegistry()
            )));
        }
        return new BaseConditionResult(
                basicDBObject, projectionCondition(wrapper.getProjectionList()),
                sortCond
        );
    }

    public void logic(QueryChainWrapper<?, ?> queryChainWrapper, MongoPlusBasicDBObject basicDBObject, Function<List<Bson>, Bson> function) {
        List<Bson> bsonList = new ArrayList<>();
        queryChainWrapper.getCompareList().forEach(compareCondition -> {
            if (Objects.equals(COMBINE.getValue(), compareCondition.getCondition())) {
                bsonList.add(queryCondition(
                        ((QueryChainWrapper<?, ?>) compareCondition.getValue())
                                .getCompareList()
                ));
            } else {
                bsonList.add(queryCondition(compareCondition));
            }
        });
        bsonList.addAll(queryChainWrapper.getBasicDBObjectList());
        basicDBObject.put(function.apply(bsonList));
    }

}
