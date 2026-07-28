package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_customize;

import java.lang.reflect.Field;

public final class FieldValidator__F_customize {
    private FieldValidator__F_customize() {
    }

    public static void validate(Field field, F_customize annotation) {
        if (annotation.var() != '0') {
            ValidatorUtil.validateNumericField(field, "@F_customize");
        }
        ValidatorUtil.validateLocalVariable(field, "@F_customize", annotation.var(), false);
    }
}
