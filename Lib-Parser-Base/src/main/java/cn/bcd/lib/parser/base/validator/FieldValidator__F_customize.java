package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_customize;
import cn.bcd.lib.parser.base.processor.Processor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class FieldValidator__F_customize {
    private FieldValidator__F_customize() {
    }

    public static void validate(Field field, F_customize annotation) {
        Class<?> processorClass = annotation.processorClass();
        if (!Processor.class.isAssignableFrom(processorClass)
                || processorClass.isInterface() || Modifier.isAbstract(processorClass.getModifiers())
                || !Modifier.isPublic(processorClass.getModifiers())) {
            ValidatorUtil.fail("{} @F_customize processorClass[{}] must be a public concrete Processor",
                    ValidatorUtil.fieldDescription(field), processorClass.getName());
        }
        ValidatorUtil.validateVariable(field, "@F_customize", annotation.var(), annotation.globalVar());
    }
}
