package cn.bcd.lib.spring.database.common.condition.impl;

import cn.bcd.lib.spring.database.common.condition.Condition;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/** 由 AND 或 OR 连接的组合条件。 */
public final class ConcatCondition implements Condition {
    @Serial
    private static final long serialVersionUID = 1L;
    public final ConcatWay concatWay;
    public final List<Condition> conditions;

    public ConcatCondition(ConcatWay concatWay, List<Condition> conditions) {
        this.concatWay = Objects.requireNonNull(concatWay, "concatWay");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        if (this.conditions.isEmpty()) {
            throw new IllegalArgumentException("conditions must not be empty");
        }
    }

    public enum ConcatWay { AND, OR }
}
