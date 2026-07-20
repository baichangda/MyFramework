package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_bean_list;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

public final class FieldValidator__F_bean_list {
    private FieldValidator__F_bean_list() {
    }

    public static void validate(Field field, F_bean_list annotation) {
        ValidatorUtil.validateRequiredLengthPair(ValidatorUtil.fieldDescription(field), "@F_bean_list",
                annotation.listLen(), annotation.listLenExpr());
        if (field.getType().isArray()) {
            return;
        }
        if (!List.class.isAssignableFrom(field.getType())) {
            ValidatorUtil.fail("{} @F_bean_list requires an array or List field", ValidatorUtil.fieldDescription(field));
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)
                || parameterizedType.getActualTypeArguments().length != 1
                || !(parameterizedType.getActualTypeArguments()[0] instanceof Class<?>)) {
            ValidatorUtil.fail("{} @F_bean_list requires a concrete List element type", ValidatorUtil.fieldDescription(field));
        }
    }
}
