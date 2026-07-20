package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_bit_num;

import java.lang.reflect.Field;

public final class FieldValidator__F_bit_num {
    private FieldValidator__F_bit_num() {
    }

    public static void validate(Field field, F_bit_num annotation) {
        ValidatorUtil.validateNumericField(field, "@F_bit_num");
        ValidatorUtil.validateVariable(field, "@F_bit_num", annotation.var(), annotation.globalVar());
        ValidatorUtil.validateSkipVariable(field, "@F_bit_num", annotation.skip(), annotation.var(), annotation.globalVar());
        ValidatorUtil.validateRange(field, "@F_bit_num len", annotation.len(), 1, 64);
        ValidatorUtil.validateNonNegative(field, "@F_bit_num skipBefore", annotation.skipBefore());
        ValidatorUtil.validateNonNegative(field, "@F_bit_num skipAfter", annotation.skipAfter());
        ValidatorUtil.validatePrecision(field, "@F_bit_num precision", annotation.precision());
    }
}
