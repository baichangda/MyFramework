package cn.bcd.lib.database.common.condition.impl;


import cn.bcd.lib.database.common.condition.Condition;

/**
 * 日期类型条件
 * 当val==null时候忽略此条件
 */
public class NumberCondition implements Condition {
    public final Handler handler;
    public final String fieldName;
    public final Object val;

    private NumberCondition(String fieldName, Object val, Handler handler) {
        this.fieldName = fieldName;
        this.val = val;
        this.handler = handler;
    }

    public static NumberCondition EQUAL(String fieldName, Number val) {
        return new NumberCondition(fieldName, val, Handler.EQUAL);
    }

    public static NumberCondition NOT_EQUAL(String fieldName, Number val) {
        return new NumberCondition(fieldName, val, Handler.NOT_EQUAL);
    }

    public static NumberCondition LT(String fieldName, Number val) {
        return new NumberCondition(fieldName, val, Handler.LT);
    }

    public static NumberCondition LE(String fieldName, Number val) {
        return new NumberCondition(fieldName, val, Handler.LE);
    }

    public static NumberCondition GT(String fieldName, Number val) {
        return new NumberCondition(fieldName, val, Handler.GT);
    }

    public static NumberCondition GE(String fieldName, Number val) {
        return new NumberCondition(fieldName, val, Handler.GE);
    }

    public static NumberCondition IN(String fieldName, Number ... val) {
        return new NumberCondition(fieldName, val, Handler.IN);
    }

    public static NumberCondition NOT_IN(String fieldName, Number ... val) {
        return new NumberCondition(fieldName, val, Handler.NOT_IN);
    }

    public enum Handler {
        /**
         * 等于
         */
        EQUAL,
        /**
         * 不等于
         */
        NOT_EQUAL,
        /**
         * 小于
         */
        LT,
        /**
         * 小于等于
         */
        LE,
        /**
         * 大于
         */
        GT,
        /**
         * 大于等于
         */
        GE,
        /**
         * 在...之内
         */
        IN,
        /**
         * 不在...之内
         */
        NOT_IN
    }

}
