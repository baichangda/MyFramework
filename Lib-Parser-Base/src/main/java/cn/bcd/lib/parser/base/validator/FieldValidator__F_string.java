package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_string;

import java.lang.reflect.Field;

public final class FieldValidator__F_string {
    private FieldValidator__F_string() {
    }

    public static void validate(Field field, F_string annotation) {
        ValidatorUtil.validateFieldType(field, "@F_string", String.class);
        try {
            java.nio.charset.Charset.forName(annotation.charset());
        } catch (RuntimeException e) {
            ValidatorUtil.fail("{} @F_string charset[{}] is invalid", ValidatorUtil.fieldDescription(field), annotation.charset());
        }
        ValidatorUtil.validateRequiredLengthPair(ValidatorUtil.fieldDescription(field), "@F_string",
                annotation.len(), annotation.lenExpr());
    }
}
