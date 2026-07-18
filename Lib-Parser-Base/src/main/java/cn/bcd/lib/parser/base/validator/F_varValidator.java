package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_var;

import java.lang.reflect.Field;

public final class F_varValidator {
    private F_varValidator() {
    }

    public static void validate(Field field, F_var annotation, boolean hasParserAnnotation) {
        if (!hasParserAnnotation) {
            ValidatorUtil.fail("class[{}] field[{}] @F_var must be used with exactly one parser field annotation",
                    field.getDeclaringClass().getName(), field.getName());
        }
        if (annotation.index() < 0) {
            ValidatorUtil.fail("class[{}] field[{}] @F_var index[{}] must not be negative",
                    field.getDeclaringClass().getName(), field.getName(), annotation.index());
        }
    }
}
