package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.base.exception.BaseException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;

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

    static void validateRequiredExpression(Field field, String annotation, String expression) {
        if (expression.isBlank()) {
            fail("{} {} requires an expression", fieldDescription(field), annotation);
        }
        validateExpression(fieldDescription(field), annotation, expression);
    }

    static void validateArrayField(Field field, String annotation) {
        if (!field.getType().isArray()) {
            fail("{} {} requires an array field", fieldDescription(field), annotation);
        }
    }

    static void validateFieldType(Field field, String annotation, Class<?>... supportedTypes) {
        Class<?> fieldType = field.getType();
        if (Arrays.stream(supportedTypes).noneMatch(type -> type == fieldType)) {
            fail("{} {} does not support field type[{}]", fieldDescription(field), annotation, fieldType.getName());
        }
    }

    static void validateNumericField(Field field, String annotation) {
        Class<?> type = field.getType();
        if (type != byte.class && type != short.class && type != int.class && type != long.class
                && type != float.class && type != double.class && !type.isEnum()) {
            fail("{} {} requires a numeric primitive or enum field", fieldDescription(field), annotation);
        }
    }

    static void validateNumericArrayField(Field field, String annotation) {
        validateArrayField(field, annotation);
        Class<?> type = field.getType().componentType();
        if (type != byte.class && type != short.class && type != int.class && type != long.class
                && type != float.class && type != double.class && !type.isEnum()) {
            fail("{} {} requires a numeric primitive or enum array field", fieldDescription(field), annotation);
        }
    }

    static void validateDateField(Field field, String annotation) {
        Class<?> type = field.getType();
        if (!Date.class.isAssignableFrom(type) && !Instant.class.isAssignableFrom(type)
                && !LocalDateTime.class.isAssignableFrom(type) && !OffsetDateTime.class.isAssignableFrom(type)
                && !ZonedDateTime.class.isAssignableFrom(type) && type != long.class
                && !String.class.isAssignableFrom(type)) {
            fail("{} {} does not support field type[{}]", fieldDescription(field), annotation, type.getName());
        }
    }

    static void validateVariable(Field field, String annotation, char var, char globalVar) {
        if (var != '0' && (var < 'a' || var > 'z')) {
            fail("{} {} var[{}] must be in [a-z]", fieldDescription(field), annotation, var);
        }
        if (globalVar != '0' && (globalVar < 'A' || globalVar > 'Z')) {
            fail("{} {} globalVar[{}] must be in [A-Z]", fieldDescription(field), annotation, globalVar);
        }
    }

    static void validateSkipVariable(Field field, String annotation, boolean skip, char var, char globalVar) {
        if (skip && (var != '0' || globalVar != '0')) {
            fail("{} {} skip cannot be used with var or globalVar", fieldDescription(field), annotation);
        }
    }

    static void validateCompanionField(Field field, String annotation, Class<?> expectedType) {
        String companionName = field.getName() + "__v";
        final Field companion;
        try {
            companion = field.getDeclaringClass().getField(companionName);
        } catch (NoSuchFieldException e) {
            fail("{} {} requires companion field[{}]", fieldDescription(field), annotation, companionName);
            return;
        }
        if (companion.getType() != expectedType) {
            fail("{} {} companion field[{}] must have type[{}]", fieldDescription(field), annotation, companionName, expectedType.getTypeName());
        }
    }

    static void validateZoneId(Field field, String annotation, String property, String value) {
        try {
            ZoneId.of(value);
        } catch (RuntimeException e) {
            fail("{} {} {}[{}] is invalid", fieldDescription(field), annotation, property, value);
        }
    }

    static void validateDateFormat(Field field, String annotation, String format) {
        try {
            DateTimeFormatter.ofPattern(format);
        } catch (RuntimeException e) {
            fail("{} {} stringFormat[{}] is invalid", fieldDescription(field), annotation, format);
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
