package cn.bcd.lib.spring.database.common.condition;


import cn.bcd.lib.spring.database.common.condition.impl.ConcatCondition;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by Administrator on 2017/4/11.
 */
public interface Condition extends Serializable {
    static Condition and(List<Condition> conditions) {
        List<Condition> list = conditions.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (list.isEmpty()) {
            return null;
        }
        return new ConcatCondition(ConcatCondition.ConcatWay.AND, list);
    }

    static Condition and(Condition... conditions) {
        return and(Arrays.asList(conditions));
    }

    static Condition or(List<Condition> conditions) {
        List<Condition> list = conditions.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (list.isEmpty()) {
            return null;
        }
        return new ConcatCondition(ConcatCondition.ConcatWay.OR, list);
    }

    static Condition or(Condition... conditions) {
        return or(Arrays.asList(conditions));
    }
}
