package cn.bcd.lib.spring.database.mongo.condition.converter;

import cn.bcd.lib.spring.database.common.condition.Converter;
import cn.bcd.lib.spring.database.common.condition.impl.BooleanCondition;
import org.springframework.data.mongodb.core.query.Criteria;

public class BooleanConditionConverter implements Converter<BooleanCondition, Criteria> {
    @Override
    public Criteria convert(BooleanCondition condition, Object... exts) {
        return condition.val == null ? null : Criteria.where(condition.fieldName).is(condition.val);
    }
}
