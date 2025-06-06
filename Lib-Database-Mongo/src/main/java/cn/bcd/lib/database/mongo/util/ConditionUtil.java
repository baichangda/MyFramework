package cn.bcd.lib.database.mongo.util;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.database.common.condition.Condition;
import cn.bcd.lib.database.common.condition.Converter;
import cn.bcd.lib.database.common.condition.impl.*;
import cn.bcd.lib.database.mongo.condition.converter.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Administrator on 2017/4/11.
 */
public class ConditionUtil {
    private final static Map<Class<? extends Condition>, Converter<? extends Condition, ?>> CONDITION_CONVERTER_MAP = new HashMap<>();

    static {
        CONDITION_CONVERTER_MAP.put(NumberCondition.class, new NumberConditionConverter());
        CONDITION_CONVERTER_MAP.put(StringCondition.class, new StringConditionConverter());
        CONDITION_CONVERTER_MAP.put(DateCondition.class, new DateConditionConverter());
        CONDITION_CONVERTER_MAP.put(NullCondition.class, new NullConditionConverter());
        CONDITION_CONVERTER_MAP.put(ConcatCondition.class, new ConcatConditionConverter());
    }

    public static <T extends Condition> Query toQuery(T condition) {
        Criteria criteria = convertCondition(condition);
        if (criteria == null) {
            return new Query();
        }
        return new Query(criteria);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Condition> Criteria convertCondition(T condition) {
        if (condition == null) {
            return null;
        }
        Converter<T, ?> converter = (Converter<T, ?>) CONDITION_CONVERTER_MAP.get(condition.getClass());
        if (converter == null) {
            throw BaseException.get("[ConditionUtil.convertCondition],Condition[" + condition.getClass() + "] Have Not Converter!");
        } else {
            return (Criteria) converter.convert(condition);
        }
    }
}
