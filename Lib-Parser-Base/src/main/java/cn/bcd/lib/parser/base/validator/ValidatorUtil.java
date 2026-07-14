package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.base.exception.BaseException;

import java.lang.reflect.Field;

final class ValidatorUtil {
    private ValidatorUtil() {
    }

    static void validateRequiredLengthPair(String owner, String annotation, int len, String expression) {
        validateLengthPair(owner, annotation, len, expression);
        if (len == 0 && expression.isBlank()) {
            fail("{} {} must define length or expression", owner, annotation);
        }
    }

    static void validateLengthPair(String owner, String annotation, int len, String expression) {
        if (len < 0) {
            fail("{} {} length must not be negative: {}", owner, annotation, len);
        }
        if (len != 0 && !expression.isBlank()) {
            fail("{} {} length and expression are mutually exclusive", owner, annotation);
        }
        if (!expression.isBlank()) {
            validateExpression(owner, annotation, expression);
        }
    }

    private static void validateExpression(String owner, String annotation, String expression) {
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c) || Character.isLetterOrDigit(c)
                    || c == '+' || c == '-' || c == '*' || c == '/') {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth < 0) {
                fail("{} {} expression has unbalanced parentheses: {}", owner, annotation, expression);
            } else if (c != ')') {
                fail("{} {} expression contains unsupported character[{}]: {}", owner, annotation, c, expression);
            }
        }
        if (depth != 0) {
            fail("{} {} expression has unbalanced parentheses: {}", owner, annotation, expression);
        }
    }

    static String fieldDescription(Field field) {
        return "class[" + field.getDeclaringClass().getName() + "] field[" + field.getName() + "]";
    }

    static void validateArrayField(Field field, String annotation) {
        if (!field.getType().isArray()) {
            fail("{} {} requires an array field", fieldDescription(field), annotation);
        }
    }

    static void validateNonNegative(Field field, String property, int value) {
        if (value < 0) {
            fail("{} {} must not be negative: {}", fieldDescription(field), property, value);
        }
    }

    static void validatePrecision(Field field, String property, int precision) {
        if (precision < -1 || precision > 10) {
            fail("{} {} must be between -1 and 10: {}", fieldDescription(field), property, precision);
        }
    }

    static void validateRange(Field field, String property, int value, int min, int max) {
        if (value < min || value > max) {
            fail("{} {} must be between {} and {}: {}", fieldDescription(field), property, min, max, value);
        }
    }

    static void fail(String message, Object... args) {
        throw BaseException.get(message, args);
    }
}
