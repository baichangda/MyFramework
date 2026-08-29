package cn.bcd.lib.spring.database.common.condition.impl;

import cn.bcd.lib.spring.database.common.condition.Condition;

import java.io.Serial;

/** 字符串类型条件；val 为 null 时下游转换器忽略此条件。 */
public final class StringCondition implements Condition {
    @Serial
    private static final long serialVersionUID = 1L;
    public final Handler handler;
    public final String fieldName;
    public final Object val;

    private StringCondition(String fieldName, Object val, Handler handler) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        this.fieldName = fieldName;
        this.val = val;
        this.handler = handler;
    }

    public static StringCondition EQUAL(String fieldName, String val) { return new StringCondition(fieldName, val, Handler.EQUAL); }
    public static StringCondition NOT_EQUAL(String fieldName, String val) { return new StringCondition(fieldName, val, Handler.NOT_EQUAL); }
    public static StringCondition ALL_LIKE(String fieldName, String val) { return new StringCondition(fieldName, val, Handler.ALL_LIKE); }
    public static StringCondition LEFT_LIKE(String fieldName, String val) { return new StringCondition(fieldName, val, Handler.LEFT_LIKE); }
    public static StringCondition RIGHT_LIKE(String fieldName, String val) { return new StringCondition(fieldName, val, Handler.RIGHT_LIKE); }
    public static StringCondition IN(String fieldName, String... val) { return new StringCondition(fieldName, val, Handler.IN); }
    public static StringCondition NOT_IN(String fieldName, String... val) { return new StringCondition(fieldName, val, Handler.NOT_IN); }

    public enum Handler { EQUAL, NOT_EQUAL, ALL_LIKE, LEFT_LIKE, RIGHT_LIKE, IN, NOT_IN }
}
