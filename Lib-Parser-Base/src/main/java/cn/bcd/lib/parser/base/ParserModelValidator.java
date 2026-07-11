package cn.bcd.lib.parser.base;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.anno.C_skip;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.anno.F_skip;
import cn.bcd.lib.parser.base.data.NumValGetter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

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
    }

    private static boolean hasParserAnnotation(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (Parser.anno_fieldBuilder.containsKey(annotation.annotationType())) {
                return true;
            }
        }
        return false;
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

    private static void fail(String message, Object... args) {
        throw BaseException.get(message, args);
    }
}
