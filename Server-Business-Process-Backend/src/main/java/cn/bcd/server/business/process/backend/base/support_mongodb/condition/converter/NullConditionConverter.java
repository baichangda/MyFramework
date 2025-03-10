package cn.bcd.server.business.process.backend.base.support_mongodb.condition.converter;

import cn.bcd.server.business.process.backend.base.condition.Converter;
import cn.bcd.server.business.process.backend.base.condition.impl.NullCondition;
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
