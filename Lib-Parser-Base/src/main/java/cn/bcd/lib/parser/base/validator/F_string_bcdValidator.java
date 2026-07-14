package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_string_bcd;

import java.lang.reflect.Field;

public final class F_string_bcdValidator {
    private F_string_bcdValidator() {
    }

    public static void validate(Field field, F_string_bcd annotation) {
        ValidatorUtil.validateRequiredLengthPair(ValidatorUtil.fieldDescription(field), "@F_string_bcd",
                annotation.len(), annotation.lenExpr());
    }
}
