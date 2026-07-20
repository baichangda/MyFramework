package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_skip;

import java.lang.reflect.Field;

public final class FieldValidator__F_skip {
    private FieldValidator__F_skip() {
    }

    public static void validate(Field field, F_skip annotation) {
        String owner = ValidatorUtil.fieldDescription(field);
        ValidatorUtil.validateLengthPair(owner, "@F_skip before", annotation.lenBefore(), annotation.lenExprBefore());
        ValidatorUtil.validateLengthPair(owner, "@F_skip after", annotation.lenAfter(), annotation.lenExprAfter());
    }
}
