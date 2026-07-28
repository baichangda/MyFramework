package cn.bcd.lib.parser.base.builder;


import cn.bcd.lib.parser.base.anno.F_customize;
import cn.bcd.lib.parser.base.util.ParseUtil;

import java.lang.reflect.Field;

public class FieldBuilder__F_customize extends FieldBuilder {
    @Override
    public void buildParse(BuilderContext context) {
        final Field field = context.field;
        final F_customize anno = field.getAnnotation(F_customize.class);
        final Class<?> processorClass = anno.processorClass();
        final StringBuilder body = context.method_body;
        final String varNameField = ParseUtil.getFieldVarName(context);
        final String processorClassVarName = context.getCustomizeProcessorVarName(processorClass, anno.processorArgs());
        final String varNameInstance = FieldBuilder.varNameInstance;
        final Class<?> fieldType = field.getType();
        final String fieldTypeClassName = fieldType.getName();
        final String processContextVarName = context.getProcessContextVarName();
        final String unBoxing = ParseUtil.unBoxing(ParseUtil.format("{}.process({},{})", processorClassVarName, FieldBuilder.varNameByteBuf, processContextVarName), fieldType);
        ParseUtil.append(body, "final {} {}={};\n", fieldTypeClassName, varNameField, unBoxing);
        ParseUtil.append(body, "{}.{}={};\n", varNameInstance, field.getName(), varNameField);
    }

    @Override
    public void buildDeParse(BuilderContext context) {
        final Field field = context.field;
        final F_customize anno = field.getAnnotation(F_customize.class);
        final Class<?> processorClass = anno.processorClass();
        final StringBuilder body = context.method_body;
        final String varInstanceName = FieldBuilder.varNameInstance;
        final String valCode = varInstanceName + "." + field.getName();

        final String processContextVarName = context.getProcessContextVarName();
        final String processorClassVarName = context.getCustomizeProcessorVarName(processorClass, anno.processorArgs());
        ParseUtil.append(body, "{}.deProcess({},{},{});\n", processorClassVarName, FieldBuilder.varNameByteBuf, processContextVarName, ParseUtil.boxing(valCode, field.getType()));
    }

}
