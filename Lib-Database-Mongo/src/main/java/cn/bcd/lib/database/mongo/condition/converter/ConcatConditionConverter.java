package cn.bcd.lib.database.mongo.condition.converter;

import cn.bcd.lib.database.common.condition.Condition;
import cn.bcd.lib.database.common.condition.Converter;
import cn.bcd.lib.database.common.condition.impl.ConcatCondition;
import cn.bcd.lib.database.mongo.util.ConditionUtil;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;
import java.util.Objects;


/**
 * Created by Administrator on 2017/9/15.
 */
public class ConcatConditionConverter implements Converter<ConcatCondition, Criteria> {
    @Override
    public Criteria convert(ConcatCondition condition, Object... exts) {
        List<Condition> conditionList = condition.conditions;
        ConcatCondition.ConcatWay concatWay = condition.concatWay;
        Criteria[] criterias = conditionList.stream().map(ConditionUtil::convertCondition).filter(Objects::nonNull).toArray(Criteria[]::new);
        if (criterias.length == 0) {
            return null;
        }
        switch (concatWay) {
            case AND: {
                return new Criteria().andOperator(criterias);
            }
            case OR: {
                return new Criteria().orOperator(criterias);
            }
            default: {
                return null;
            }
        }
    }
}
