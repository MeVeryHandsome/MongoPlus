package com.mongoplus.listener.business;

import com.mongoplus.cache.global.OrderCache;
import com.mongoplus.listener.Listener;
import com.mongoplus.logging.Log;
import com.mongoplus.logging.LogFactory;
import com.mongoplus.model.command.CommandFailed;
import com.mongoplus.model.command.CommandStarted;
import com.mongoplus.model.command.CommandSucceeded;
import com.mongoplus.toolkit.MongoCommandBuildUtils;

import java.util.Objects;

/**
 * Mongo拦截器，这里可以打印日志
 *
 * @author JiaChaoYang
 * @date 2023/11/22 10:54
 */
public class LogListener implements Listener {

    private static final Log log = LogFactory.getLog(LogListener.class);
    private boolean pretty;

    public boolean isPretty() {
        return pretty;
    }

    public void setPretty(boolean pretty) {
        this.pretty = pretty;
    }

    public LogListener(boolean pretty) {
        this.pretty = pretty;
    }

    public LogListener() {
    }

    @Override
    public void commandStarted(CommandStarted commandStarted) {
        log.info(commandStarted.getCommandName() + " Statement Execution ==> ");
        if (isPretty()) {
            log.info(MongoCommandBuildUtils.buildCommand(commandStarted.getCommandStartedEvent()));
        } else {
            log.info(commandStarted.getCommand());
        }
    }

    @Override
    public void commandSucceeded(CommandSucceeded commandSucceeded) {
        Integer resultCount = null;
        if (Objects.equals(commandSucceeded.getCommandName(), "find") || Objects.equals(commandSucceeded.getCommandName(), "aggregate")) {
            resultCount = commandSucceeded.getResponse().getDocument("cursor").get("firstBatch").asArray().getValues().size();
        } else if (Objects.equals(commandSucceeded.getCommandName(), "update")) {
            resultCount = commandSucceeded.getResponse().get("nModified").asInt32().getValue();
        } else if (Objects.equals(commandSucceeded.getCommandName(), "insert") || Objects.equals(commandSucceeded.getCommandName(), "delete")) {
            resultCount = commandSucceeded.getResponse().get("n").asInt32().getValue();
        }
        if (resultCount != null) {
            log.info(commandSucceeded.getCommandName() + " results of execution ==> " + resultCount);
        }
    }

    @Override
    public void commandFailed(CommandFailed commandFailed) {
        String commandName = commandFailed.getCommandName();
        Throwable throwable = commandFailed.getThrowable();
        log.error("error ==> : " + commandName + ", " + throwable.getMessage());
    }

    @Override
    public int getOrder() {
        return OrderCache.LOG_ORDER;
    }
}
