package cn.bcd.lib.database.mongo.condition.converter;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.database.common.condition.Converter;
import cn.bcd.lib.database.common.condition.impl.StringCondition;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Administrator on 2017/9/15.
 */
public class StringConditionConverter implements Converter<StringCondition, Criteria> {
    @Override
    public Criteria convert(StringCondition condition, Object... exts) {
        String fieldName = condition.fieldName;
        Object val = condition.val;
        StringCondition.Handler handler = condition.handler;
        if (val == null) {
            return null;
        } else {
            switch (handler) {
                case EQUAL: {
                    return Criteria.where(fieldName).is(val);
                }
                case NOT_EQUAL: {
                    return Criteria.where(fieldName).ne(val);
                }
                case ALL_LIKE: {
                    return Criteria.where(fieldName).regex(".*(" + val + ").*");
                }
                case LEFT_LIKE: {
                    return Criteria.where(fieldName).regex("^(" + val + ")");
                }
                case RIGHT_LIKE: {
                    return Criteria.where(fieldName).regex("(" + val + ")$");
                }
                case IN: {
                    if (val.getClass().isArray()) {
                        List<Object> list = new ArrayList<>();
                        int length = Array.getLength(val);
                        for (int i = 0; i < length; i++) {
                            Object o = Array.get(val, i);
                            if (o != null) {
                                list.add(o);
                            }
                        }
                        if (list.isEmpty()) {
                            return null;
                        } else {
                            return Criteria.where(fieldName).in(list);
                        }
                    } else {
                        throw BaseException.get("type[{}] not support", val.getClass().getName());
                    }
                }
                case NOT_IN: {
                    if (val.getClass().isArray()) {
                        List<Object> list = new ArrayList<>();
                        int length = Array.getLength(val);
                        for (int i = 0; i < length; i++) {
                            Object o = Array.get(val, i);
                            if (o != null) {
                                list.add(o);
                            }
                        }
                        if (list.isEmpty()) {
                            return null;
                        } else {
                            return Criteria.where(fieldName).nin(list);
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

    public static void main(String[] args) {
        Criteria criteria = Criteria.where("id").in(Arrays.asList("1", "2"));
        Query query = Query.query(criteria);
        System.out.println(query);
    }

}
