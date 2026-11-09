package cn.bcd.lib.spring.database.common.condition;

import cn.bcd.lib.spring.database.common.condition.impl.BooleanCondition;
import cn.bcd.lib.spring.database.common.condition.impl.ConcatCondition;
import cn.bcd.lib.spring.database.common.condition.impl.DateCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NullCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NumberCondition;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 查询条件的公共类型；JSON 使用固定 type 标识还原具体条件，支持嵌套组合。 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringCondition.class, name = "string"),
        @JsonSubTypes.Type(value = NumberCondition.class, name = "number"),
        @JsonSubTypes.Type(value = DateCondition.class, name = "date"),
        @JsonSubTypes.Type(value = BooleanCondition.class, name = "boolean"),
        @JsonSubTypes.Type(value = NullCondition.class, name = "null"),
        @JsonSubTypes.Type(value = ConcatCondition.class, name = "concat")
})
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
