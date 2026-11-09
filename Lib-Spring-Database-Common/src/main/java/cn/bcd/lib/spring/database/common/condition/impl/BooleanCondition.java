package cn.bcd.lib.spring.database.common.condition.impl;

import cn.bcd.lib.spring.database.common.condition.Condition;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;

/** 布尔值相等条件；null 表示不参与筛选，false 是有效条件。 */
public final class BooleanCondition implements Condition {
    @Serial
    private static final long serialVersionUID = 1L;
    public final String fieldName;
    public final Boolean val;

    @JsonCreator
    private BooleanCondition(@JsonProperty("fieldName") String fieldName, @JsonProperty("val") Boolean val) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        this.fieldName = fieldName;
        this.val = val;
    }

    public static BooleanCondition EQUAL(String fieldName, Boolean val) {
        return new BooleanCondition(fieldName, val);
    }
}
