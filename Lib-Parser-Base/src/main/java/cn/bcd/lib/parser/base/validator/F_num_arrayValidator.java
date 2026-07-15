package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumValGetter;

import java.lang.reflect.Field;

public final class F_num_arrayValidator {
    private F_num_arrayValidator() {
    }

    public static void validate(Field field, F_num_array annotation, NumValGetter numValGetter) {
        NumType singleType = annotation.singleType();
        if (annotation.singleCheckVal() && (singleType == NumType.float32 || singleType == NumType.float64)) {
            ValidatorUtil.fail("{} does not support value checking for NumType {}",
                    ValidatorUtil.fieldDescription(field), singleType);
        }
        if (numValGetter == null && annotation.singleCheckVal()) {
            ValidatorUtil.fail("{} requires NumValGetter because value checking is enabled",
                    ValidatorUtil.fieldDescription(field));
        }
        ValidatorUtil.validateArrayField(field, "@F_num_array");
        ValidatorUtil.validateRequiredLengthPair(ValidatorUtil.fieldDescription(field), "@F_num_array",
                annotation.len(), annotation.lenExpr());
        ValidatorUtil.validateNonNegative(field, "@F_num_array singleSkip", annotation.singleSkip());
        ValidatorUtil.validatePrecision(field, "@F_num_array singlePrecision", annotation.singlePrecision());
    }
}
