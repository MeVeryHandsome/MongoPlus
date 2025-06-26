package com.mongoplus.sharding;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongoplus.constant.DataSourceConstant;
import com.mongoplus.domain.MongoPlusException;
import com.mongoplus.enums.ExecuteMethodEnum;
import com.mongoplus.toolkit.ArrayUtils;
import com.mongoplus.toolkit.CollUtil;
import com.mongoplus.toolkit.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static com.mongoplus.enums.ExecuteMethodEnum.*;

/**
 * 分片处理器
 * 
 * @author anwen
 */
public abstract class AbstractDataSourceShardingHandler {

    /**
     * 处理数据源名称的缓存类
     */
    private final Map<String, List<String>> handleDsNameCache = new ConcurrentHashMap<>();

    /**
     * 判断是否是正则的缓存
     */
    private final Map<String, Boolean> regexCache = new ConcurrentHashMap<>();

    private static final String REGEX = "^(?!master$).*";

    /**
     * 分片策略
     */
    private Map<String, List<ExecuteMethodEnum>> shardingStrategy = new ConcurrentHashMap<>();

    /**
     * 默认的分片策略
     * <p>
     * 增删改使用master，查询使用从数据源
     * </p>
     */
    public static final Map<String, List<ExecuteMethodEnum>> DEFAULT_SHARDING_STRATEGY = new ConcurrentHashMap<>();

    /**
     * 自定义的分片策略
     */
    private DataSourceShardingStrategy dataSourceShardingStrategy;

    /**
     * 处理后的分片策略
     */
    private Map<ExecuteMethodEnum, List<String>> handleShardingStrategy = new ConcurrentHashMap<>();

    static {
        DEFAULT_SHARDING_STRATEGY.put(DataSourceConstant.DEFAULT_DATASOURCE, Arrays.asList(
                SAVE,
                REMOVE,
                UPDATE,
                BULK_WRITE));
        DEFAULT_SHARDING_STRATEGY.put(REGEX, Arrays.asList(
                QUERY,
                AGGREGATE,
                COUNT,
                ESTIMATED_DOCUMENT_COUNT));
    }

    public AbstractDataSourceShardingHandler() {
        handle();
    }

    public AbstractDataSourceShardingHandler(Map<String, List<ExecuteMethodEnum>> shardingStrategy) {
        this.shardingStrategy = shardingStrategy;
        handle();
    }

    public DataSourceShardingStrategy getDataSourceShardingStrategy() {
        return dataSourceShardingStrategy;
    }

    /**
     * 设置自定义分片策略
     * 
     * @param dataSourceShardingStrategy 策略
     * @author anwen
     */
    public void setDataSourceShardingStrategy(DataSourceShardingStrategy dataSourceShardingStrategy) {
        this.dataSourceShardingStrategy = dataSourceShardingStrategy;
    }

    /**
     * 根据method获取对应的数据源
     * 
     * @param method 执行器方法
     * @return {@link List<String>}
     * @author anwen
     */
    public List<String> getHandleShardingStrategy(ExecuteMethodEnum method) {
        return handleShardingStrategy.get(method);
    }

    public Map<ExecuteMethodEnum, List<String>> getHandleShardingStrategy() {
        return handleShardingStrategy;
    }

    /**
     * 处理数据源名称，可以使用*通配符和正则表达式
     * 
     * @param pattern            数据源名称
     * @param originalDsNameList 对应的所有数据源名称
     * @return {@link List<String>}
     * @author anwen
     */
    public List<String> handleDsName(String pattern, List<String> originalDsNameList) {
        return handleDsNameCache.computeIfAbsent(pattern, key -> {
            List<String> resultList;
            if (isValidRegex(pattern)) {
                resultList = handleDsNameByRegex(pattern, originalDsNameList);
            } else if (pattern.contains("*")) {
                String[] parts = pattern.split("\\*");
                resultList = originalDsNameList.stream()
                        .filter(str -> {
                            int startIndex = 0;
                            boolean isMatch = true;

                            // 遍历分割后的模式部分，依次进行匹配
                            for (String part : parts) {
                                if (StringUtils.isEmpty(part)) {
                                    // 空字符串表示 '*'，跳过这个部分
                                    continue;
                                }

                                startIndex = str.indexOf(part, startIndex);
                                if (startIndex == -1) {
                                    isMatch = false;
                                    break;
                                }
                                // 移动到下一个搜索位置
                                startIndex += part.length();
                            }
                            return isMatch;
                        })
                        .collect(Collectors.toList());
            } else {
                resultList = originalDsNameList.stream()
                        .filter(str -> str.equals(pattern))
                        .collect(Collectors.toList());
            }
            return resultList;
        });
    }

    /**
     * 处理数据源名称，正则匹配
     * 
     * @param regex              正则
     * @param originalDsNameList 对应的所有数据源名称
     * @return {@link List<String>}
     * @author anwen
     */
    public List<String> handleDsNameByRegex(String regex, List<String> originalDsNameList) {
        return originalDsNameList.stream()
                .filter(str -> Pattern.compile(regex).matcher(str).matches())
                .collect(Collectors.toList());
    }

    /**
     * 数据源负载均衡
     * <p>
     * {@link DataSourceShardingHandler}默认实现，使用加权随机算法选择数据源
     * </p>
     * 
     * @param dsNameList 数据源集合
     * @return {@link String}
     * @author anwen
     */
    public abstract String loadBalance(List<String> dsNameList);

    /**
     * 事务处理
     * 
     * @param currentClientSession 当前{@code session}
     * @param mongoClient          新数据源的客户端
     * @return {@link ClientSession} 命中新数据源后的{@code session}
     */
    public abstract ClientSession handleTransactional(ClientSession currentClientSession, MongoClient mongoClient);

    /**
     * 构建器
     * 
     * @return {@link DataSourceShardingBuild}
     * @author anwen
     */
    public static DataSourceShardingBuild builder() {
        return new DataSourceShardingBuild();
    }

    public Map<String, List<ExecuteMethodEnum>> getShardingStrategy() {
        return shardingStrategy;
    }

    void setShardingStrategy(Map<String, List<ExecuteMethodEnum>> shardingStrategy) {
        this.shardingStrategy = shardingStrategy;
    }

    void addShardingStrategy(String dsName, List<ExecuteMethodEnum> method) {
        this.shardingStrategy.put(dsName, method);
    }

    /**
     * 将分片策略进一步处理
     * 
     * @author anwen
     */
    void handle() {
        if (CollUtil.isEmpty(shardingStrategy)) {
            shardingStrategy = DEFAULT_SHARDING_STRATEGY;
        }
        handleShardingStrategy = shardingStrategy.entrySet().stream()
                // 扁平化 entry 为 (ExecuteMethodEnum,String)对
                .flatMap(entry -> entry.getValue().stream()
                        .map(method -> new AbstractMap.SimpleEntry<>(method, entry.getKey())))
                // 分组并映射为目标格式
                .collect(Collectors.groupingBy(
                        // 按 ExecuteMethodEnum分组
                        Map.Entry::getKey,
                        // 收集每个 ExecuteMethodEnum 对应的 List<String>
                        Collectors.mapping(Map.Entry::getValue, Collectors.toCollection(CopyOnWriteArrayList::new))));
    }

    /**
     * 清空数据源名称处理缓存
     * 
     * @author anwen
     */
    public void clearHandleDsNameCache() {
        synchronized (handleDsNameCache) {
            handleDsNameCache.clear();
        }
    }

    /**
     * 清空正则校验缓存
     * 
     * @author anwen
     */
    public void clearRegexCache() {
        synchronized (regexCache) {
            regexCache.clear();
        }
    }

    public boolean isValidRegex(String regex) {
        return regexCache.computeIfAbsent(regex, k -> {
            boolean isRegex = false;
            try {
                Pattern.compile(regex);
                isRegex = true;
            } catch (PatternSyntaxException ignored) {
            }
            return isRegex;
        });
    }

    public static class DataSourceShardingBuild {

        DataSourceShardingHandler handler = new DataSourceShardingHandler();

        /**
         * 设置分片映射
         * 
         * @param dsName 数据源名称
         * @param method 所执行的操作
         * @return {@link DataSourceShardingBuild}
         * @author anwen
         */
        public DataSourceShardingBuild setShardingMapping(String dsName, List<ExecuteMethodEnum> method) {
            if (CollUtil.isEmpty(method)) {
                throw new MongoPlusException("method is null");
            }
            handler.addShardingStrategy(dsName, method);
            return this;
        }

        /**
         * 设置分片映射
         * 
         * @param dsName 数据源名称
         * @param method 所执行的操作
         * @return {@link DataSourceShardingBuild}
         * @author anwen
         */
        public DataSourceShardingBuild setShardingMapping(String dsName, ExecuteMethodEnum... method) {
            if (ArrayUtils.isEmpty(method)) {
                throw new MongoPlusException("method is null");
            }
            handler.addShardingStrategy(dsName, Arrays.stream(method).collect(Collectors.toList()));
            return this;
        }

        public AbstractDataSourceShardingHandler build() {
            handler.handle();
            return handler;
        }

    }

}
