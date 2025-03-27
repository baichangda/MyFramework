package cn.bcd.lib.database.mongo.condition.converter;

import cn.bcd.lib.database.common.condition.Converter;
import cn.bcd.lib.database.common.condition.impl.NullCondition;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Created by Administrator on 2017/9/15.
 */
public class NullConditionConverter implements Converter<NullCondition, Criteria> {
    @Override
    public Criteria convert(NullCondition condition, Object... exts) {
        String fieldName = condition.fieldName;
        NullCondition.Handler handler = condition.handler;
        return switch (handler) {
            case NULL -> {
                Criteria criteria = Criteria.where(fieldName);
                yield  criteria.orOperator(criteria.exists(false), criteria.exists(true).is(null));
            }
            case NOT_NULL -> Criteria.where(fieldName).exists(true).ne(null);
        };
    }
}
