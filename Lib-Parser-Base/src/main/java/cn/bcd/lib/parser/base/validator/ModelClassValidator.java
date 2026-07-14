package cn.bcd.lib.parser.base.validator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public final class ModelClassValidator {
    private ModelClassValidator() {
    }

    public static void validate(Class<?> clazz) {
        int modifiers = clazz.getModifiers();
        if (clazz.isInterface() || Modifier.isAbstract(modifiers)) {
            ValidatorUtil.fail("class[{}] must be a concrete class", clazz.getName());
        }
        try {
            Constructor<?> constructor = clazz.getConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                ValidatorUtil.fail("class[{}] must have a public no-argument constructor", clazz.getName());
            }
        } catch (NoSuchMethodException e) {
            ValidatorUtil.fail("class[{}] must have a public no-argument constructor", clazz.getName());
        }
    }
}
