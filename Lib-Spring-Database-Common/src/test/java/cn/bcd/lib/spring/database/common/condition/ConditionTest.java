package cn.bcd.lib.spring.database.common.condition;

import cn.bcd.lib.spring.database.common.condition.impl.ConcatCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NumberCondition;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConditionTest {
    @Test
    void filtersNullAndAvoidsSingleElementConcat() {
        Condition condition = StringCondition.EQUAL("name", "demo");
        assertSame(condition, Condition.and(null, condition, null));
        assertSame(condition, Condition.or(List.of(condition)));
        assertThrows(NullPointerException.class, () -> Condition.and((Condition[]) null));
    }

    @Test
    void rejectsInvalidDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> StringCondition.EQUAL(" ", "value"));
        assertThrows(NullPointerException.class, () -> new ConcatCondition(null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ConcatCondition(ConcatCondition.ConcatWay.AND, List.of()));
    }
}
