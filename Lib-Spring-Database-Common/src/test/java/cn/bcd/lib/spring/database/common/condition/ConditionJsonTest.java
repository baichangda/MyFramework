package cn.bcd.lib.spring.database.common.condition;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.database.common.condition.impl.BooleanCondition;
import cn.bcd.lib.spring.database.common.condition.impl.ConcatCondition;
import cn.bcd.lib.spring.database.common.condition.impl.DateCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NullCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NumberCondition;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionJsonTest {
    @Test
    void roundTripsEveryOperatorAndNestedConditions() {
        List<Condition> conditions = List.of(
                StringCondition.EQUAL("name", "中文"),
                StringCondition.NOT_EQUAL("name", "a"),
                StringCondition.ALL_LIKE("name", "a"),
                StringCondition.LEFT_LIKE("name", "a"),
                StringCondition.RIGHT_LIKE("name", "a"),
                StringCondition.IN("name", "a", null, "b"),
                StringCondition.NOT_IN("name"),
                NumberCondition.EQUAL("id", Long.MAX_VALUE),
                NumberCondition.NOT_EQUAL("id", 1),
                NumberCondition.LT("id", 1),
                NumberCondition.LE("id", 1),
                NumberCondition.GT("id", 1),
                NumberCondition.GE("id", 1),
                NumberCondition.IN("id", 1, null, Long.MAX_VALUE),
                NumberCondition.NOT_IN("id"),
                DateCondition.EQUAL("created", new Date(123)),
                DateCondition.LE("created", new Date(123)),
                DateCondition.LT("created", new Date(123)),
                DateCondition.GE("created", new Date(123)),
                DateCondition.GT("created", new Date(123)),
                DateCondition.BETWEEN("created", null, new Date(456)),
                BooleanCondition.EQUAL("enabled", false),
                NullCondition.NULL("deleted"),
                NullCondition.NOT_NULL("name"));
        Condition original = Condition.and(conditions);
        Condition restored = JsonUtil.fromJson(JsonUtil.toJson(original), Condition.class);
        assertInstanceOf(ConcatCondition.class, restored);
        assertEquals(JsonUtil.toJson(original), JsonUtil.toJson(restored));
        ConcatCondition nested = assertInstanceOf(ConcatCondition.class,
                roundTrip(Condition.or(original, BooleanCondition.EQUAL("enabled", true))));
        assertEquals(ConcatCondition.ConcatWay.OR, nested.concatWay);
        assertEquals(conditions.size(), ((ConcatCondition) nested.conditions.getFirst()).conditions.size());
    }

    @Test
    void restoresArrayAndDateTypesRequiredByConverters() {
        StringCondition strings = (StringCondition) roundTrip(StringCondition.IN("name", "a", null));
        assertArrayEquals(new String[]{"a", null}, assertInstanceOf(String[].class, strings.val));
        NumberCondition numbers = (NumberCondition) roundTrip(NumberCondition.IN("id", 1, null, Long.MAX_VALUE));
        assertArrayEquals(new Number[]{1, null, Long.MAX_VALUE}, assertInstanceOf(Number[].class, numbers.val));
        DateCondition date = (DateCondition) roundTrip(DateCondition.EQUAL("created", new Date(-123)));
        assertEquals(new Date(-123), assertInstanceOf(Date.class, date.val));
        DateCondition range = (DateCondition) roundTrip(DateCondition.BETWEEN("created", null, new Date(456)));
        assertArrayEquals(new Date[]{null, new Date(456)}, assertInstanceOf(Date[].class, range.val));
    }

    @Test
    void preservesLargeIntegersAndDecimalPrecision() {
        BigDecimal decimal = new BigDecimal("12345678901234567890.12345678901234567890");
        BigInteger integer = new BigInteger("123456789012345678901234567890");
        NumberCondition scalar = (NumberCondition) roundTrip(NumberCondition.EQUAL("amount", decimal));
        assertEquals(0, decimal.compareTo(assertInstanceOf(BigDecimal.class, scalar.val)));
        NumberCondition array = (NumberCondition) roundTrip(NumberCondition.IN("amount", decimal, integer));
        Number[] values = assertInstanceOf(Number[].class, array.val);
        assertEquals(0, decimal.compareTo(assertInstanceOf(BigDecimal.class, values[0])));
        assertEquals(integer, values[1]);
    }

    @Test
    void supportsNullValuesAndNullRoot() {
        assertNull(JsonUtil.fromJson("null", Condition.class));
        List<Condition> conditions = List.of(
                StringCondition.EQUAL("name", null),
                StringCondition.IN("name", (String[]) null),
                NumberCondition.EQUAL("id", null),
                NumberCondition.IN("id", (Number[]) null),
                DateCondition.EQUAL("created", null),
                DateCondition.BETWEEN("created", null, null),
                BooleanCondition.EQUAL("enabled", null));
        for (Condition condition : conditions) {
            assertEquals(JsonUtil.toJson(condition), JsonUtil.toJson(roundTrip(condition)));
        }
    }

    @Test
    void worksInsideDtosAndWithConcreteTypes() {
        Payload payload = new Payload(Condition.and(
                StringCondition.IN("name", "a"), BooleanCondition.EQUAL("enabled", false)));
        Payload restored = JsonUtil.fromJson(JsonUtil.toJson(payload), Payload.class);
        assertEquals(JsonUtil.toJson(payload.condition()), JsonUtil.toJson(restored.condition()));
        StringCondition condition = StringCondition.EQUAL("name", "a");
        assertEquals("a", JsonUtil.fromJson(JsonUtil.toJson(condition), StringCondition.class).val);
    }

    @Test
    void rejectsInvalidTypesOperatorsAndDefinitions() {
        String[] invalid = {
                "{}",
                "{\"type\":\"unknown\",\"fieldName\":\"name\"}",
                "{\"type\":\"string\",\"fieldName\":\"name\",\"handler\":\"UNKNOWN\"}",
                "{\"type\":\"number\",\"fieldName\":\"id\"}",
                "{\"type\":\"number\",\"fieldName\":\"id\",\"handler\":null}",
                "{\"type\":\"null\",\"fieldName\":\" \",\"handler\":\"NULL\"}",
                "{\"type\":\"concat\",\"concatWay\":\"AND\",\"conditions\":[]}",
                "{\"type\":\"date\",\"fieldName\":\"created\",\"handler\":\"BETWEEN\",\"val\":[123]}",
                "{\"type\":\"number\",\"fieldName\":\"id\",\"handler\":\"IN\",\"val\":{}}"
        };
        for (String json : invalid) {
            assertThrows(RuntimeException.class, () -> JsonUtil.fromJson(json, Condition.class), json);
        }
    }

    private Condition roundTrip(Condition condition) {
        Condition restored = JsonUtil.fromJson(JsonUtil.toJson(condition), Condition.class);
        assertEquals(condition.getClass(), restored.getClass());
        return restored;
    }

    record Payload(Condition condition) {}
}
