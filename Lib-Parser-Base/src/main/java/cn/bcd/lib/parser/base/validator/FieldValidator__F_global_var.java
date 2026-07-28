package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_easy;
import cn.bcd.lib.parser.base.anno.F_global_var;
import cn.bcd.lib.parser.base.anno.F_num;

import java.lang.reflect.Field;

public final class FieldValidator__F_global_var {
    private FieldValidator__F_global_var() {
    }

    public static void validate(Field field, F_global_var annotation) {
        String var = annotation.var();
        if (var.isBlank()) {
            ValidatorUtil.fail("{} @F_global_var var must not be blank",
                    ValidatorUtil.fieldDescription(field));
        }
        if (var.length() == 1 && var.charAt(0) >= 'a' && var.charAt(0) <= 'z') {
            ValidatorUtil.fail("{} @F_global_var var[{}] must not be a single lowercase letter",
                    ValidatorUtil.fieldDescription(field), var);
        }
        if (var.chars().allMatch(Character::isDigit)) {
            ValidatorUtil.fail("{} @F_global_var var[{}] must not contain only digits",
                    ValidatorUtil.fieldDescription(field), var);
        }
        boolean skip = switch (field.getAnnotation(F_num.class)) {
            case F_num value -> value.skip();
            case null -> switch (field.getAnnotation(F_bit_num.class)) {
                case F_bit_num value -> value.skip();
                case null -> {
                    F_bit_num_easy value = field.getAnnotation(F_bit_num_easy.class);
                    yield value != null && value.skip();
                }
            };
        };
        if (skip) {
            ValidatorUtil.fail("{} skipped field cannot use @F_global_var",
                    ValidatorUtil.fieldDescription(field));
        }
    }
}
