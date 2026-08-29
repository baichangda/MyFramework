package cn.bcd.lib.spring.database.common.condition.impl;

import cn.bcd.lib.spring.database.common.condition.Condition;

import java.io.Serial;

/** null 判断条件。 */
public final class NullCondition implements Condition {
    @Serial
    private static final long serialVersionUID = 1L;
    public final Handler handler;
    public final String fieldName;

    private NullCondition(String fieldName, Handler handler) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        this.fieldName = fieldName;
        this.handler = handler;
    }

    public static NullCondition NULL(String fieldName) { return new NullCondition(fieldName, Handler.NULL); }
    public static NullCondition NOT_NULL(String fieldName) { return new NullCondition(fieldName, Handler.NOT_NULL); }

    public enum Handler { NULL, NOT_NULL }
}
