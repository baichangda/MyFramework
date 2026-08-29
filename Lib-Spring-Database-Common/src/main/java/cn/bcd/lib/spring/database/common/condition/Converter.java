package cn.bcd.lib.spring.database.common.condition;

/** 将通用查询条件转换为目标数据库查询对象。 */
@FunctionalInterface
public interface Converter<T extends Condition, R> {
    R convert(T condition, Object... exts);
}
