package cn.bcd.lib.spring.database.common.condition.impl;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.database.common.condition.Condition;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.util.Date;
import java.util.Objects;

/** 日期类型条件；BETWEEN 使用前闭后开区间。 */
public final class DateCondition implements Condition {
    @Serial
    private static final long serialVersionUID = 1L;
    public final Handler handler;
    public final String fieldName;
    public final Object val;

    private DateCondition(String fieldName, Object val, Handler handler) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        this.fieldName = fieldName;
        this.val = val;
        this.handler = handler;
    }

    @JsonCreator
    private static DateCondition fromJson(@JsonProperty("fieldName") String fieldName,
                                          @JsonProperty("val") Object val,
                                          @JsonProperty(value = "handler", required = true) Handler handler) {
        Objects.requireNonNull(handler, "handler");
        Class<?> valueType = handler == Handler.BETWEEN ? Date[].class : Date.class;
        Object value = JsonUtil.MAPPER.convertValue(val, valueType);
        if (handler == Handler.BETWEEN && (value == null || ((Date[]) value).length != 2)) {
            throw new IllegalArgumentException("BETWEEN requires exactly two dates");
        }
        return new DateCondition(fieldName, value, handler);
    }

    public static DateCondition EQUAL(String fieldName, Date val) { return new DateCondition(fieldName, val, Handler.EQUAL); }
    public static DateCondition LE(String fieldName, Date val) { return new DateCondition(fieldName, val, Handler.LE); }
    public static DateCondition LT(String fieldName, Date val) { return new DateCondition(fieldName, val, Handler.LT); }
    public static DateCondition GE(String fieldName, Date val) { return new DateCondition(fieldName, val, Handler.GE); }
    public static DateCondition GT(String fieldName, Date val) { return new DateCondition(fieldName, val, Handler.GT); }
    public static DateCondition BETWEEN(String fieldName, Date start, Date end) {
        return new DateCondition(fieldName, new Date[]{start, end}, Handler.BETWEEN);
    }

    public enum Handler { EQUAL, LE, LT, GE, GT, BETWEEN }
}
