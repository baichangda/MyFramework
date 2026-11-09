package cn.bcd.lib.spring.database.common.condition.impl;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.database.common.condition.Condition;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.util.TokenBuffer;

import java.io.Serial;
import java.util.Objects;

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

    /** 保留 JSON 数字精度，避免先按 Object 解析为 Double。 */
    @JsonCreator
    private static NumberCondition fromJson(@JsonProperty("fieldName") String fieldName,
                                            @JsonProperty("val") TokenBuffer val,
                                            @JsonProperty(value = "handler", required = true) Handler handler) {
        Objects.requireNonNull(handler, "handler");
        Class<?> valueType = handler == Handler.IN || handler == Handler.NOT_IN ? Number[].class : Number.class;
        Object value = val == null ? null : JsonUtil.MAPPER.readerFor(valueType)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readValue(val.asParser());
        return new NumberCondition(fieldName, value, handler);
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
