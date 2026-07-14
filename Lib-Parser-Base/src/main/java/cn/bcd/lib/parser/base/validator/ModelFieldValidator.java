package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.builder.FieldBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

public final class ModelFieldValidator {
    private ModelFieldValidator() {
    }

    public static Annotation validate(Field field,
                                      Map<Class<? extends Annotation>, FieldBuilder> fieldBuilders) {
        Annotation parserAnnotation = null;
        int parserAnnotationCount = 0;
        for (Annotation annotation : field.getAnnotations()) {
            if (fieldBuilders.containsKey(annotation.annotationType())) {
                parserAnnotation = annotation;
                parserAnnotationCount++;
            }
        }
        if (parserAnnotationCount == 0) {
            return null;
        }
        int modifiers = field.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
            ValidatorUtil.fail("class[{}] field[{}] must be public, non-static and non-final",
                    field.getDeclaringClass().getName(), field.getName());
        }
        if (parserAnnotationCount != 1) {
            ValidatorUtil.fail("class[{}] field[{}] must have exactly one parser field annotation, actual[{}]",
                    field.getDeclaringClass().getName(), field.getName(), parserAnnotationCount);
        }
        return parserAnnotation;
    }
}
