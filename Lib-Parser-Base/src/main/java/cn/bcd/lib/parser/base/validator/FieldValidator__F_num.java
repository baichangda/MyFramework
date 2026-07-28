package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumValGetter;

import java.lang.reflect.Field;

public final class FieldValidator__F_num {
    private FieldValidator__F_num() {
    }

    public static void validate(Field field, F_num annotation, NumValGetter numValGetter) {
        ValidatorUtil.validateNumericField(field, "@F_num");
        ValidatorUtil.validateVariable(field, "@F_num", annotation.var(), annotation.globalVar());
        ValidatorUtil.validateSkipVariable(field, "@F_num", annotation.skip(), annotation.var(), annotation.globalVar());
        if (annotation.checkVal()) {
            ValidatorUtil.validateCompanionField(field, "@F_num checkVal", byte.class);
        }
        NumType type = annotation.type();
        if (annotation.checkVal() && (type == NumType.float32 || type == NumType.float64)) {
            ValidatorUtil.fail("{} does not support value checking for NumType {}",
                    ValidatorUtil.fieldDescription(field), type);
        }
        if (numValGetter == null && annotation.checkVal()) {
            ValidatorUtil.fail("{} requires NumValGetter because value checking is enabled",
                    ValidatorUtil.fieldDescription(field));
        }
        ValidatorUtil.validatePrecision(field, "@F_num precision", annotation.precision());
    }
}
