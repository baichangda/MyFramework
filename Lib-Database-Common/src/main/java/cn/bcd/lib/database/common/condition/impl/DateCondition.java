package cn.bcd.lib.database.common.condition.impl;

import cn.bcd.lib.database.common.condition.Condition;

import java.util.Date;

/**
 * 日期类型条件
 * 当val==null时候忽略此条件
 */
public class DateCondition implements Condition {
    public final Handler handler;
    public final String fieldName;
    public final Object val;

    private DateCondition(String fieldName, Object val, Handler handler) {
        this.fieldName = fieldName;
        this.val = val;
        this.handler = handler;
    }

    public static DateCondition EQUAL(String fieldName, Date val) {
        return new DateCondition(fieldName, val, Handler.EQUAL);
    }

    public static DateCondition LE(String fieldName, Date val) {
        return new DateCondition(fieldName, val, Handler.LE);
    }

    public static DateCondition LT(String fieldName, Date val) {
        return new DateCondition(fieldName, val, Handler.LT);
    }

    public static DateCondition GE(String fieldName, Date val) {
        return new DateCondition(fieldName, val, Handler.GE);
    }

    public static DateCondition GT(String fieldName, Date val) {
        return new DateCondition(fieldName, val, Handler.GT);
    }

    public static DateCondition BETWEEN(String fieldName, Date start, Date end) {
        return new DateCondition(fieldName, new Date[]{start, end}, Handler.BETWEEN);
    }

    public enum Handler {
        /**
         * 等于
         */
        EQUAL,
        /**
         * 小于等于
         */
        LE,
        /**
         * 小于
         */
        LT,
        /**
         * 大于等于
         */
        GE,
        /**
         * 大于
         */
        GT,
        /**
         * 在时间范围内、前闭后开[date1,date2)
         */
        BETWEEN
    }
}
