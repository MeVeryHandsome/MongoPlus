package com.mongoplus.model.command;

import org.bson.conversions.Bson;

import java.util.List;

/**
 * 解析器响应对象
 * @author anwen
 */
public class ParseCommand {

    /**
     * 操作行为
     * @date 2024/11/15 14:13
     */
    private String operate;

    /**
     * 集合
     * @date 2024/11/15 14:14
     */
    private String collection;

    /**
     * 命令
     * @date 2024/11/15 14:15
     */
    private Object command;

    /**
     * 提取但未解析的命令
     * @date 2024/11/15 14:51
     */
    private String unresolvedCommand;

    /**
     * 原始command
     * @date 2024/11/15 14:16
     */
    private String original;

    public String getOperate() {
        return operate;
    }

    public void setOperate(String operate) {
        this.operate = operate;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public Object getCommand() {
        return command;
    }

    public Bson getBsonCommand(){
        return (Bson) command;
    }

    @SuppressWarnings("unchecked")
    public List<Bson> getBsonListCommand(){
        return (List<Bson>) command;
    }

    public void setCommand(Object command) {
        this.command = command;
    }

    public String getUnresolvedCommand() {
        return unresolvedCommand;
    }

    public void setUnresolvedCommand(String unresolvedCommand) {
        this.unresolvedCommand = unresolvedCommand;
    }

    public String getOriginal() {
        return original;
    }

    public void setOriginal(String original) {
        this.original = original;
    }

    @Override
    public String toString() {
        return "ParseCommand{" +
                "operate='" + operate + '\'' +
                ", collection='" + collection + '\'' +
                ", command=" + command +
                ", unresolvedCommand='" + unresolvedCommand + '\'' +
                ", original='" + original + '\'' +
                '}';
    }
}
