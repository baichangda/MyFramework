package cn.bcd.lib.spring.database.common.condition.impl;

import cn.bcd.lib.spring.database.common.condition.Condition;

import java.io.Serial;

/** 数值类型条件；val 为 null 时下游转换器忽略此条件。 */
public final class NumberCondition implements Condition {
    @Serial
    private static final long serialVersionUID = 1L;
    public final Handler handler;
    public final String fieldName;
    public final Object val;

    private NumberCondition(String fieldName, Object val, Handler handler) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        this.fieldName = fieldName;
        this.val = val;
        this.handler = handler;
    }

    public static NumberCondition EQUAL(String fieldName, Number val) { return new NumberCondition(fieldName, val, Handler.EQUAL); }
    public static NumberCondition NOT_EQUAL(String fieldName, Number val) { return new NumberCondition(fieldName, val, Handler.NOT_EQUAL); }
    public static NumberCondition LT(String fieldName, Number val) { return new NumberCondition(fieldName, val, Handler.LT); }
    public static NumberCondition LE(String fieldName, Number val) { return new NumberCondition(fieldName, val, Handler.LE); }
    public static NumberCondition GT(String fieldName, Number val) { return new NumberCondition(fieldName, val, Handler.GT); }
    public static NumberCondition GE(String fieldName, Number val) { return new NumberCondition(fieldName, val, Handler.GE); }
    public static NumberCondition IN(String fieldName, Number... val) { return new NumberCondition(fieldName, val, Handler.IN); }
    public static NumberCondition NOT_IN(String fieldName, Number... val) { return new NumberCondition(fieldName, val, Handler.NOT_IN); }

    public enum Handler { EQUAL, NOT_EQUAL, LT, LE, GT, GE, IN, NOT_IN }
}
