package cn.bcd.lib.spring.database.jdbc.condition;

import cn.bcd.lib.spring.database.common.condition.Converter;
import cn.bcd.lib.spring.database.common.condition.impl.BooleanCondition;
import cn.bcd.lib.spring.database.jdbc.service.BeanInfo;

import java.util.ArrayList;
import java.util.List;

public class BooleanConditionConverter implements Converter<BooleanCondition, ConvertRes> {
    @Override
    public ConvertRes convert(BooleanCondition condition, Object... exts) {
        if (condition.val == null) {
            return null;
        }
        BeanInfo<?> info = (BeanInfo<?>) exts[0];
        return new ConvertRes(info.toColumnName(condition.fieldName) + "=?", new ArrayList<>(List.of(condition.val)));
    }
}
