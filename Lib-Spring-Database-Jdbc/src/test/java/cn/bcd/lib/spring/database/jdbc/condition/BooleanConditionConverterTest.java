package cn.bcd.lib.spring.database.jdbc.condition;

import cn.bcd.lib.spring.database.common.condition.impl.BooleanCondition;
import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.SuperBaseBean;
import cn.bcd.lib.spring.database.jdbc.service.BeanInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BooleanConditionConverterTest {
    @Table("test_boolean")
    public static class Bean extends SuperBaseBean {
        public Boolean isEnabled;
    }

    @Test
    void nullIsIgnoredAndBooleanValuesUseMappedColumnsAndParameters() {
        BeanInfo<Bean> info = new BeanInfo<>(Bean.class);
        assertNull(ConditionUtil.convertCondition(BooleanCondition.EQUAL("isEnabled", null), info));
        for (boolean value : List.of(false, true)) {
            var result = ConditionUtil.convertCondition(BooleanCondition.EQUAL("isEnabled", value), info);
            assertEquals("is_enabled=?", result.sql);
            assertEquals(List.of(value), result.paramList);
        }
    }
}
