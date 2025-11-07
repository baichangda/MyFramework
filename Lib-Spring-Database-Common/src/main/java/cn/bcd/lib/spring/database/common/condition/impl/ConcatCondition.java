package cn.bcd.lib.spring.database.common.condition.impl;

import cn.bcd.lib.spring.database.common.condition.Condition;

import java.util.List;

/**
 * Created by Administrator on 2017/3/23.
 */
public class ConcatCondition implements Condition {
    public final ConcatWay concatWay;
    public final List<Condition> conditions;

    public ConcatCondition(ConcatWay concatWay, List<Condition> conditions) {
        this.concatWay = concatWay;
        this.conditions = conditions;
    }

    public enum ConcatWay {
        AND,
        OR
    }

}
