package cn.bcd.business.backend.base.support_jdbc.condition;

import cn.bcd.base.exception.BaseException;
import cn.bcd.business.backend.base.condition.Converter;
import cn.bcd.business.backend.base.condition.impl.StringCondition;
import cn.bcd.business.backend.base.support_jdbc.service.BeanInfo;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Created by Administrator on 2017/9/15.
 */
public class StringConditionConverter implements Converter<StringCondition, ConvertRes> {
    @Override
    public ConvertRes convert(StringCondition condition, Object... exts) {
        final Object val = condition.val;
        if (val == null) {
            return null;
        }
        final String fieldName = condition.fieldName;
        final StringCondition.Handler handler = condition.handler;
        final BeanInfo<?> beanInfo = (BeanInfo<?>) exts[0];
        final String columnName = beanInfo.toColumnName(fieldName);
        switch (handler) {
            case EQUAL: {
                return new ConvertRes(columnName + "=?", new ArrayList<>(List.of(val)));
            }
            case NOT_EQUAL: {
                return new ConvertRes(columnName + "<>?", new ArrayList<>(List.of(val)));
            }
            case ALL_LIKE: {
                return new ConvertRes(columnName + " like ?", new ArrayList<>(List.of("%" + val + "%")));
            }
            case LEFT_LIKE: {
                return new ConvertRes(columnName + " like ?", new ArrayList<>(List.of("%" + val)));
            }
            case RIGHT_LIKE: {
                return new ConvertRes(columnName + " like ?", new ArrayList<>(List.of(val+ "%")));
            }
            case IN: {
                if (val.getClass().isArray()) {
                    StringBuilder sql = new StringBuilder();
                    List<Object> paramList = new ArrayList<>();
                    int length = Array.getLength(val);
                    sql.append(columnName);
                    sql.append(" in (");
                    StringJoiner sj = new StringJoiner(",");
                    for (int i = 0; i < length; i++) {
                        Object o = Array.get(val, i);
                        if (o != null) {
                            sj.add("?");
                            paramList.add(o);
                        }
                    }
                    sql.append(sj);
                    sql.append(")");
                    if (paramList.isEmpty()) {
                        return null;
                    }else{
                        return new ConvertRes(sql.toString(), paramList);
                    }
                } else {
                    throw BaseException.get("type[{}] not support", val.getClass().getName());
                }
            }
            case NOT_IN: {
                if (val.getClass().isArray()) {
                    StringBuilder sql = new StringBuilder();
                    List<Object> paramList = new ArrayList<>();
                    int length = Array.getLength(val);
                    sql.append(columnName);
                    sql.append(" not in (");
                    StringJoiner sj = new StringJoiner(",");
                    for (int i = 0; i < length; i++) {
                        Object o = Array.get(val, i);
                        if (o != null) {
                            sj.add("?");
                            paramList.add(o);
                        }
                    }
                    sql.append(sj);
                    sql.append(")");
                    if (paramList.isEmpty()) {
                        return null;
                    }else{
                        return new ConvertRes(sql.toString(), paramList);
                    }
                } else {
                    throw BaseException.get("type[{}] not support", val.getClass().getName());
                }
            }
            default: {
                throw BaseException.get("handler[{}] not support", handler);
            }
        }
    }

}
