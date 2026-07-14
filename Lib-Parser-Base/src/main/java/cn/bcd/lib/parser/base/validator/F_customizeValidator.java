package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_customize;

import java.lang.reflect.Field;

public final class F_customizeValidator {
    private F_customizeValidator() {
    }

    public static void validate(Field field, F_customize annotation) {
        // Reserved for F_customize-specific constraints.
    }
}
