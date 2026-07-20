package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_bit_num_array;

import java.lang.reflect.Field;

public final class FieldValidator__F_bit_num_array {
    private FieldValidator__F_bit_num_array() {
    }

    public static void validate(Field field, F_bit_num_array annotation) {
        ValidatorUtil.validateNumericArrayField(field, "@F_bit_num_array");
        ValidatorUtil.validateRequiredLengthPair(ValidatorUtil.fieldDescription(field), "@F_bit_num_array",
                annotation.len(), annotation.lenExpr());
        ValidatorUtil.validateRange(field, "@F_bit_num_array singleLen", annotation.singleLen(), 1, 64);
        ValidatorUtil.validateNonNegative(field, "@F_bit_num_array singleSkip", annotation.singleSkip());
        ValidatorUtil.validateNonNegative(field, "@F_bit_num_array skipBefore", annotation.skipBefore());
        ValidatorUtil.validateNonNegative(field, "@F_bit_num_array skipAfter", annotation.skipAfter());
        ValidatorUtil.validatePrecision(field, "@F_bit_num_array singlePrecision", annotation.singlePrecision());
    }
}
