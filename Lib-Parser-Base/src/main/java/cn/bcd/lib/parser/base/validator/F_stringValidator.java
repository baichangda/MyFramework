package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_string;

import java.lang.reflect.Field;

public final class F_stringValidator {
    private F_stringValidator() {
    }

    public static void validate(Field field, F_string annotation) {
        ValidatorUtil.validateRequiredLengthPair(ValidatorUtil.fieldDescription(field), "@F_string",
                annotation.len(), annotation.lenExpr());
    }
}
