package cn.bcd.lib.spring.database.common.condition.impl;

import cn.bcd.lib.spring.database.common.condition.Condition;

import java.io.Serial;

/**
 * 是否为null条件
 * 不依赖val
 */
public class NullCondition implements Condition {
    @Serial
    private static final long serialVersionUID = 1L;
    public final Handler handler;
    public final String fieldName;

    private NullCondition(String fieldName, Handler handler) {
        this.fieldName = fieldName;
        this.handler = handler;
    }

    public static NullCondition NULL(String fieldName) {
        return new NullCondition(fieldName, Handler.NULL);
    }

    public static NullCondition NOT_NULL(String fieldName) {
        return new NullCondition(fieldName, Handler.NOT_NULL);
    }

    public enum Handler {
        /**
         * 为空
         */
        NULL,
        /**
         * 不为空
         */
        NOT_NULL
    }
}
