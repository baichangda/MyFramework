package cn.bcd.lib.parser.base;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.anno.*;
import cn.bcd.lib.parser.base.data.NumValGetter;
import cn.bcd.lib.parser.base.util.ParseUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/** Validates parser models before Java source generation starts. */
final class ParserModelValidator {
    private ParserModelValidator() {
    }

    static void validate(Class<?> clazz, NumValGetter numValGetter) {
        validateClass(clazz);
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (hasParserAnnotation(field)) {
                    validateField(field, numValGetter);
                }
            }
        }
        C_skip cSkip = clazz.getAnnotation(C_skip.class);
        if (cSkip != null) {
            validateLengthPair(clazz.getName(), "@C_skip", cSkip.len(), cSkip.lenExpr());
            if (cSkip.len() > 0) {
                int modelLength = ParseUtil.getClassByteLenIfPossible(clazz);
                if (modelLength > cSkip.len()) {
                    fail("class[{}] @C_skip length[{}] is smaller than model length[{}]",
                            clazz.getName(), cSkip.len(), modelLength);
                }
            }
        }
    }

    private static void validateClass(Class<?> clazz) {
        int modifiers = clazz.getModifiers();
        if (clazz.isInterface() || Modifier.isAbstract(modifiers)) {
            fail("class[{}] must be a concrete class", clazz.getName());
        }
        try {
            Constructor<?> constructor = clazz.getConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                fail("class[{}] must have a public no-argument constructor", clazz.getName());
            }
        } catch (NoSuchMethodException e) {
            fail("class[{}] must have a public no-argument constructor", clazz.getName());
        }
    }

    private static void validateField(Field field, NumValGetter numValGetter) {
        int modifiers = field.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
            fail("class[{}] field[{}] must be public, non-static and non-final",
                    field.getDeclaringClass().getName(), field.getName());
        }
        int parserAnnotationCount = 0;
        for (Annotation annotation : field.getAnnotations()) {
            if (Parser.anno_fieldBuilder.containsKey(annotation.annotationType())) {
                parserAnnotationCount++;
            }
        }
        if (parserAnnotationCount != 1) {
            fail("class[{}] field[{}] must have exactly one parser field annotation, actual[{}]",
                    field.getDeclaringClass().getName(), field.getName(), parserAnnotationCount);
        }

        F_skip skip = field.getAnnotation(F_skip.class);
        if (skip != null) {
            validateLengthPair(fieldDescription(field), "@F_skip before", skip.lenBefore(), skip.lenExprBefore());
            validateLengthPair(fieldDescription(field), "@F_skip after", skip.lenAfter(), skip.lenExprAfter());
        }
        F_num num = field.getAnnotation(F_num.class);
        F_num_array numArray = field.getAnnotation(F_num_array.class);
        if (numValGetter == null && ((num != null && num.checkVal())
                || (numArray != null && numArray.singleCheckVal()))) {
            fail("{} requires NumValGetter because value checking is enabled", fieldDescription(field));
        }

        if (num != null) {
            validatePrecision(field, "@F_num precision", num.precision());
        }
        if (numArray != null) {
            validateArrayField(field, "@F_num_array");
            validateRequiredLengthPair(fieldDescription(field), "@F_num_array", numArray.len(), numArray.lenExpr());
            validateNonNegative(field, "@F_num_array singleSkip", numArray.singleSkip());
            validatePrecision(field, "@F_num_array singlePrecision", numArray.singlePrecision());
        }

        F_bit_num bitNum = field.getAnnotation(F_bit_num.class);
        if (bitNum != null) {
            validateRange(field, "@F_bit_num len", bitNum.len(), 1, 64);
            validateNonNegative(field, "@F_bit_num skipBefore", bitNum.skipBefore());
            validateNonNegative(field, "@F_bit_num skipAfter", bitNum.skipAfter());
            validatePrecision(field, "@F_bit_num precision", bitNum.precision());
        }

        F_bit_num_array bitNumArray = field.getAnnotation(F_bit_num_array.class);
        if (bitNumArray != null) {
            validateArrayField(field, "@F_bit_num_array");
            validateRequiredLengthPair(fieldDescription(field), "@F_bit_num_array", bitNumArray.len(), bitNumArray.lenExpr());
            validateRange(field, "@F_bit_num_array singleLen", bitNumArray.singleLen(), 1, 64);
            validateNonNegative(field, "@F_bit_num_array singleSkip", bitNumArray.singleSkip());
            validateNonNegative(field, "@F_bit_num_array skipBefore", bitNumArray.skipBefore());
            validateNonNegative(field, "@F_bit_num_array skipAfter", bitNumArray.skipAfter());
            validatePrecision(field, "@F_bit_num_array singlePrecision", bitNumArray.singlePrecision());
        }

        F_bit_num_easy bitNumEasy = field.getAnnotation(F_bit_num_easy.class);
        if (bitNumEasy != null) {
            validateRange(field, "@F_bit_num_easy bitStart", bitNumEasy.bitStart(), 0, 31);
            validateRange(field, "@F_bit_num_easy bitEnd", bitNumEasy.bitEnd(), 0, bitNumEasy.bitStart());
            validatePrecision(field, "@F_bit_num_easy precision", bitNumEasy.precision());
        }

        F_string string = field.getAnnotation(F_string.class);
        if (string != null) {
            validateRequiredLengthPair(fieldDescription(field), "@F_string", string.len(), string.lenExpr());
        }
        F_string_bcd stringBcd = field.getAnnotation(F_string_bcd.class);
        if (stringBcd != null) {
            validateRequiredLengthPair(fieldDescription(field), "@F_string_bcd", stringBcd.len(), stringBcd.lenExpr());
        }

        F_bean_list beanList = field.getAnnotation(F_bean_list.class);
        if (beanList != null) {
            validateRequiredLengthPair(fieldDescription(field), "@F_bean_list", beanList.listLen(), beanList.listLenExpr());
            validateBeanListField(field);
        }
    }

    private static boolean hasParserAnnotation(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (Parser.anno_fieldBuilder.containsKey(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private static void validateRequiredLengthPair(String owner, String annotation, int len, String expression) {
        validateLengthPair(owner, annotation, len, expression);
        if (len == 0 && expression.isBlank()) {
            fail("{} {} must define length or expression", owner, annotation);
        }
    }

    private static void validateLengthPair(String owner, String annotation, int len, String expression) {
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

    private static String fieldDescription(Field field) {
        return "class[" + field.getDeclaringClass().getName() + "] field[" + field.getName() + "]";
    }

    private static void validateArrayField(Field field, String annotation) {
        if (!field.getType().isArray()) {
            fail("{} {} requires an array field", fieldDescription(field), annotation);
        }
    }

    private static void validateBeanListField(Field field) {
        if (field.getType().isArray()) {
            return;
        }
        if (!List.class.isAssignableFrom(field.getType())) {
            fail("{} @F_bean_list requires an array or List field", fieldDescription(field));
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)
                || parameterizedType.getActualTypeArguments().length != 1
                || !(parameterizedType.getActualTypeArguments()[0] instanceof Class<?>)) {
            fail("{} @F_bean_list requires a concrete List element type", fieldDescription(field));
        }
    }

    private static void validateNonNegative(Field field, String property, int value) {
        if (value < 0) {
            fail("{} {} must not be negative: {}", fieldDescription(field), property, value);
        }
    }

    private static void validatePrecision(Field field, String property, int precision) {
        if (precision < -1 || precision > 10) {
            fail("{} {} must be between -1 and 10: {}", fieldDescription(field), property, precision);
        }
    }

    private static void validateRange(Field field, String property, int value, int min, int max) {
        if (value < min || value > max) {
            fail("{} {} must be between {} and {}: {}", fieldDescription(field), property, min, max, value);
        }
    }

    private static void fail(String message, Object... args) {
        throw BaseException.get(message, args);
    }
}
