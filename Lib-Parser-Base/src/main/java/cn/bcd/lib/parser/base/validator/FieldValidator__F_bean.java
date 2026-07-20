package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_bean;

import java.lang.reflect.Field;

public final class FieldValidator__F_bean {
    private FieldValidator__F_bean() {
    }

    public static void validate(Field field, F_bean annotation) {
        if (field.getType().isInterface()) {
            ValidatorUtil.validateRequiredExpression(field, "@F_bean implClassExpr", annotation.implClassExpr());
        }
    }
}
