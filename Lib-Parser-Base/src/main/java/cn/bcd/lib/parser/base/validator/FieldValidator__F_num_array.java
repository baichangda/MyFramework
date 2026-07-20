package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumValGetter;

import java.lang.reflect.Field;

public final class FieldValidator__F_num_array {
    private FieldValidator__F_num_array() {
    }

    public static void validate(Field field, F_num_array annotation, NumValGetter numValGetter) {
        ValidatorUtil.validateNumericArrayField(field, "@F_num_array");
        if (annotation.singleCheckVal()) {
            ValidatorUtil.validateCompanionField(field, "@F_num_array singleCheckVal", byte[].class);
        }
        NumType singleType = annotation.singleType();
        if (annotation.singleCheckVal() && (singleType == NumType.float32 || singleType == NumType.float64)) {
            ValidatorUtil.fail("{} does not support value checking for NumType {}",
                    ValidatorUtil.fieldDescription(field), singleType);
        }
        if (numValGetter == null && annotation.singleCheckVal()) {
            ValidatorUtil.fail("{} requires NumValGetter because value checking is enabled",
                    ValidatorUtil.fieldDescription(field));
        }
        ValidatorUtil.validateRequiredLengthPair(ValidatorUtil.fieldDescription(field), "@F_num_array",
                annotation.len(), annotation.lenExpr());
        ValidatorUtil.validateNonNegative(field, "@F_num_array singleSkip", annotation.singleSkip());
        ValidatorUtil.validatePrecision(field, "@F_num_array singlePrecision", annotation.singlePrecision());
    }
}
