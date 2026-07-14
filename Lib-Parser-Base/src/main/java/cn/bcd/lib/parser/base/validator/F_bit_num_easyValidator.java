package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_bit_num_easy;

import java.lang.reflect.Field;

public final class F_bit_num_easyValidator {
    private F_bit_num_easyValidator() {
    }

    public static void validate(Field field, F_bit_num_easy annotation) {
        ValidatorUtil.validateRange(field, "@F_bit_num_easy bitStart", annotation.bitStart(), 0, 31);
        ValidatorUtil.validateRange(field, "@F_bit_num_easy bitEnd", annotation.bitEnd(), 0, annotation.bitStart());
        ValidatorUtil.validatePrecision(field, "@F_bit_num_easy precision", annotation.precision());
    }
}
