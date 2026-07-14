package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.C_impl;

public final class C_implValidator {
    private C_implValidator() {
    }

    public static void validate(Class<?> clazz, C_impl annotation) {
        // Reserved for C_impl-specific constraints.
    }
}
