package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_string_bcd;

import java.lang.reflect.Field;

public final class FieldValidator__F_string_bcd {
    private FieldValidator__F_string_bcd() {
    }

    public static void validate(Field field, F_string_bcd annotation) {
        ValidatorUtil.validateFieldType(field, "@F_string_bcd", String.class);
        ValidatorUtil.validateRequiredLengthPair(ValidatorUtil.fieldDescription(field), "@F_string_bcd",
                annotation.len(), annotation.lenExpr());
    }
}
