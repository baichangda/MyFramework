package cn.bcd.businessProcess.backend.base.support_mongodb.util;

import cn.bcd.businessProcess.backend.base.condition.impl.*;
import cn.bcd.businessProcess.backend.base.support_mongodb.condition.converter.*;
import cn.bcd.base.exception.BaseException;
import cn.bcd.businessProcess.backend.base.condition.Condition;
import cn.bcd.businessProcess.backend.base.condition.Converter;
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