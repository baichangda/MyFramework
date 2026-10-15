package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_bean_list;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class FieldValidator__F_bean_list {
    private FieldValidator__F_bean_list() {
    }

    public static void validate(Field field, F_bean_list annotation) {
        ValidatorUtil.validateRequiredLengthPair(ValidatorUtil.fieldDescription(field), "@F_bean_list",
                annotation.listLen(), annotation.listLenExpr());
        ModelClassValidator.validate(getElementType(field));
    }

    public static Class<?> getElementType(Field field) {
        if (field.getType().isArray()) {
            return field.getType().getComponentType();
        }
        if (!List.class.isAssignableFrom(field.getType())) {
            ValidatorUtil.fail("{} @F_bean_list requires an array or List field", ValidatorUtil.fieldDescription(field));
        }
        if (!field.getType().isAssignableFrom(ArrayList.class)) {
            ModelClassValidator.validate(field.getType());
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType)) {
            ValidatorUtil.fail("{} @F_bean_list requires a concrete List element type", ValidatorUtil.fieldDescription(field));
        }
        Type[] elementTypes = ((ParameterizedType) genericType).getActualTypeArguments();
        if (elementTypes.length != 1 || !(elementTypes[0] instanceof Class<?>)) {
            ValidatorUtil.fail("{} @F_bean_list requires a concrete List element type",
                    ValidatorUtil.fieldDescription(field));
        }
        return (Class<?>) elementTypes[0];
    }
}
