package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumValGetter;

import java.lang.reflect.Field;

public final class F_numValidator {
    private F_numValidator() {
    }

    public static void validate(Field field, F_num annotation, NumValGetter numValGetter) {
        if (numValGetter == null && annotation.checkVal()) {
            ValidatorUtil.fail("{} requires NumValGetter because value checking is enabled",
                    ValidatorUtil.fieldDescription(field));
        }
        ValidatorUtil.validatePrecision(field, "@F_num precision", annotation.precision());
    }
}
