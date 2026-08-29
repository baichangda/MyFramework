package cn.bcd.lib.spring.database.common.condition;

import cn.bcd.lib.spring.database.common.condition.impl.ConcatCondition;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 查询条件的公共类型。 */
public interface Condition extends Serializable {
    static Condition and(List<Condition> conditions) {
        return concat(ConcatCondition.ConcatWay.AND, conditions);
    }

    static Condition and(Condition... conditions) {
        Objects.requireNonNull(conditions, "conditions");
        return and(Arrays.asList(conditions));
    }

    static Condition or(List<Condition> conditions) {
        return concat(ConcatCondition.ConcatWay.OR, conditions);
    }

    static Condition or(Condition... conditions) {
        Objects.requireNonNull(conditions, "conditions");
        return or(Arrays.asList(conditions));
    }

    private static Condition concat(ConcatCondition.ConcatWay way, List<Condition> conditions) {
        Objects.requireNonNull(conditions, "conditions");
        List<Condition> filtered = conditions.stream().filter(Objects::nonNull).toList();
        if (filtered.isEmpty()) {
            return null;
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }
        return new ConcatCondition(way, filtered);
    }
}
