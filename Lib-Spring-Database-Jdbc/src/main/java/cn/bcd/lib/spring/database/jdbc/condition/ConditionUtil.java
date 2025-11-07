package cn.bcd.lib.spring.database.jdbc.condition;


import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.Converter;
import cn.bcd.lib.spring.database.common.condition.impl.*;
import cn.bcd.lib.spring.database.jdbc.service.BeanInfo;

import java.util.HashMap;
import java.util.Map;

public class ConditionUtil {

    private final static Map<Class<? extends Condition>, Converter<? extends Condition,?>> CONDITION_CONVERTER_MAP = new HashMap<>();

    static {
        CONDITION_CONVERTER_MAP.put(NumberCondition.class, new NumberConditionConverter());
        CONDITION_CONVERTER_MAP.put(StringCondition.class, new StringConditionConverter());
        CONDITION_CONVERTER_MAP.put(DateCondition.class, new DateConditionConverter());
        CONDITION_CONVERTER_MAP.put(NullCondition.class, new NullConditionConverter());
        CONDITION_CONVERTER_MAP.put(ConcatCondition.class, new ConcatConditionConverter());
    }

    public static  <T extends Condition> ConvertRes convertCondition(T condition, BeanInfo<?> beanInfo) {
        return convertCondition(condition, beanInfo, true);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Condition> ConvertRes convertCondition(T condition, BeanInfo<?> beanInfo, boolean root) {
        if (condition == null) {
            return null;
        }
        Converter<T,?> converter = (Converter<T, ?>) CONDITION_CONVERTER_MAP.get(condition.getClass());
        if (converter == null) {
            throw BaseException.get("[ConditionUtil.convertCondition],Condition[" + condition.getClass() + "] Have Not Converter!");
        } else {
            return (ConvertRes) converter.convert(condition, beanInfo, root);
        }
    }
}
