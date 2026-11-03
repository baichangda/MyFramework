package cn.bcd.lib.spring.database.mongo.condition.converter;

import cn.bcd.lib.spring.database.common.condition.impl.BooleanCondition;
import cn.bcd.lib.spring.database.mongo.util.ConditionUtil;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BooleanConditionConverterTest {
    @Test
    void nullIsIgnoredAndFalseAndTrueArePreserved() {
        assertTrue(ConditionUtil.toQuery(BooleanCondition.EQUAL("enabled", null)).getQueryObject().isEmpty());
        assertEquals(new Document("enabled", false), ConditionUtil.toQuery(BooleanCondition.EQUAL("enabled", false)).getQueryObject());
        assertEquals(new Document("enabled", true), ConditionUtil.toQuery(BooleanCondition.EQUAL("enabled", true)).getQueryObject());
        assertThrows(IllegalArgumentException.class, () -> BooleanCondition.EQUAL("", true));
    }
}
