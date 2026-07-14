package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_bean;

import java.lang.reflect.Field;

public final class F_beanValidator {
    private F_beanValidator() {
    }

    public static void validate(Field field, F_bean annotation) {
        // Reserved for F_bean-specific constraints.
    }
}
