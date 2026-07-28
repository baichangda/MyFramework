package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.C_skip;
import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_easy;
import cn.bcd.lib.parser.base.anno.F_customize;
import cn.bcd.lib.parser.base.anno.F_skip;
import cn.bcd.lib.parser.base.anno.F_global_var;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.builder.BuilderContext;
import cn.bcd.lib.parser.base.builder.FieldBuilder;
import cn.bcd.lib.parser.base.log.ClassLog__C_skip;
import cn.bcd.lib.parser.base.log.FieldLog;
import cn.bcd.lib.parser.base.log.FieldLogRegistry;
import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.NumValGetter;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.base.complier.DynamicProcessorCompiler;
import cn.bcd.lib.parser.base.util.ParseUtil;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 负责生成并编译 {@link Processor} 实现，不参与生成后的解析热路径。 */
final class ProcessorSourceBuilder {
    private static final Logger logger = LoggerFactory.getLogger(ProcessorSourceBuilder.class);

    private final Class<?> modelClass;
    private final ByteOrder byteOrder;
    private final NumValGetter numValGetter;
    private final boolean parseLogging;
    private final boolean deParseLogging;
    private final boolean printBuildLog;
    private final boolean generateClassFile;
    private final StringBuilder classFields = new StringBuilder();
    private final StringBuilder constructorBody = new StringBuilder();
    private final Map<String, String> classVariableNames = new HashMap<>();
    private final List<Field> fields;

    private ProcessorSourceBuilder(Class<?> modelClass, ByteOrder byteOrder, NumValGetter numValGetter,
                                   boolean parseLogging, boolean deParseLogging,
                                   boolean printBuildLog, boolean generateClassFile) {
        this.modelClass = modelClass;
        this.byteOrder = byteOrder;
        this.numValGetter = numValGetter;
        this.parseLogging = parseLogging;
        this.deParseLogging = deParseLogging;
        this.printBuildLog = printBuildLog;
        this.generateClassFile = generateClassFile;
        fields = ParseUtil.getParseFields(modelClass);
    }

    static Class<?> build(Class<?> modelClass, ByteOrder byteOrder, NumValGetter numValGetter,
                          boolean parseLogging, boolean deParseLogging,
                          boolean printBuildLog, boolean generateClassFile) {
        return new ProcessorSourceBuilder(modelClass, byteOrder, numValGetter,
                parseLogging, deParseLogging, printBuildLog, generateClassFile).build();
    }

    private Class<?> build() {
        String processorClassName = ParseUtil.getProcessorClassName(modelClass, byteOrder, numValGetter);
        int packageSeparator = processorClassName.lastIndexOf('.');
        String packageName = processorClassName.substring(0, packageSeparator);
        String simpleClassName = processorClassName.substring(packageSeparator + 1);
        String processBody = buildMethodBody(Direction.PARSE);
        String deProcessBody = buildMethodBody(Direction.DE_PARSE);

        logGeneratedParts(processBody, deProcessBody);
        String source = buildSource(packageName, simpleClassName, processBody, deProcessBody);
        if (printBuildLog) {
            logger.info("\n-----------class[{}] source-----------\n{}\n", modelClass.getName(), source);
        }
        return DynamicProcessorCompiler.compileAndDefine(processorClassName, source, generateClassFile);
    }

    private String buildMethodBody(Direction direction) {
        StringBuilder body = new StringBuilder("\n{\n");
        if (direction == Direction.PARSE) {
            ParseUtil.append(body, "final {} {}=new {}();\n",
                    modelClass.getName(), FieldBuilder.varNameInstance, modelClass.getName());
        } else {
            ParseUtil.append(body, "final {} {}=({})$3;\n",
                    modelClass.getName(), FieldBuilder.varNameInstance, modelClass.getName());
        }
        BuilderContext context = new BuilderContext(classFields, constructorBody, body, modelClass,
                classVariableNames, byteOrder, fields, numValGetter);
        C_skip classSkip = modelClass.getAnnotation(C_skip.class);
        if (classSkip == null) {
            buildFields(context, direction);
        } else {
            buildFieldsWithClassSkip(context, classSkip, direction);
        }
        if (direction == Direction.PARSE) {
            ParseUtil.append(body, "return {};\n", FieldBuilder.varNameInstance);
        }
        return body.append('}').toString();
    }

    private void buildFields(BuilderContext context, Direction direction) {
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            context.field = field;
            context.fieldIndex = i;
            F_skip skip = field.getAnnotation(F_skip.class);
            FieldLog<?> fieldLog = findFieldLog(field);
            FieldLog<?> skipLog = skip == null ? null : FieldLogRegistry.get(F_skip.class);
            appendFieldLogBefore(context, direction, skipLog);
            appendFieldSkip(context, skip, true, direction);
            appendFieldLogBefore(context, direction, fieldLog);
            direction.build(findFieldBuilder(field), context);
            appendFieldVar(context, field, direction);
            appendFieldLogAfter(context, direction, fieldLog);
            appendFieldSkip(context, skip, false, direction);
            appendFieldLogAfter(context, direction, skipLog);
        }
    }

    private static void appendFieldVar(BuilderContext context, Field field, Direction direction) {
        F_global_var annotation = field.getAnnotation(F_global_var.class);
        Annotation parserAnnotation = findFieldBuilderAnnotation(field);
        char var = switch (parserAnnotation) {
            case F_num value -> value.var();
            case F_bit_num value -> value.var();
            case F_bit_num_easy value -> value.var();
            case F_customize value -> value.var();
            default -> '0';
        };
        String fieldValueCode = FieldBuilder.varNameInstance + "." + field.getName();
        String numericValueCode = fieldValueCode;
        if (direction == Direction.PARSE) {
            numericValueCode = switch (parserAnnotation) {
                case F_num value when !value.checkVal() -> ParseUtil.getFieldVarName(context);
                case F_bit_num ignored -> ParseUtil.getFieldVarName(context);
                case F_bit_num_easy ignored -> ParseUtil.getFieldVarName(context);
                case F_customize ignored -> ParseUtil.getFieldVarName(context);
                default -> fieldValueCode;
            };
        } else if (field.getType().isEnum()) {
            numericValueCode = fieldValueCode + ".toInteger()";
        }
        if (var != '0') {
            context.method_varToFieldName.put(var, numericValueCode);
        }
        if (annotation != null) {
            String globalVar = annotation.var()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
            boolean numericVariable = globalVar.length() == 1
                    && Character.isUpperCase(globalVar.charAt(0))
                    && (parserAnnotation instanceof F_num
                    || parserAnnotation instanceof F_bit_num
                    || parserAnnotation instanceof F_bit_num_easy
                    || parserAnnotation instanceof F_customize
                    && (field.getType().isPrimitive() || field.getType().isEnum()));
            String valueCode = numericVariable
                    ? "Integer.valueOf((int)(" + numericValueCode + "))"
                    : ParseUtil.boxing(fieldValueCode, field.getType());
            ParseUtil.append(context.method_body, "{}.putGlobalVar(\"{}\",{});\n",
                    FieldBuilder.varNameProcessContext, globalVar, valueCode);
        }
    }

    private static Annotation findFieldBuilderAnnotation(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (Parser.anno_fieldBuilder.containsKey(annotation.annotationType())) {
                return annotation;
            }
        }
        throw new IllegalStateException("No parser annotation for " + field);
    }

    private static FieldLog<?> findFieldLog(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            FieldLog<?> fieldLog = FieldLogRegistry.get(annotation.annotationType());
            if (fieldLog != null && annotation.annotationType() != F_skip.class) {
                return fieldLog;
            }
        }
        throw new IllegalStateException("No field log for " + field);
    }

    private static FieldBuilder findFieldBuilder(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            FieldBuilder builder = Parser.anno_fieldBuilder.get(annotation.annotationType());
            if (builder != null) {
                return builder;
            }
        }
        throw new IllegalStateException("No parser field builder for " + field);
    }

    private void appendFieldSkip(BuilderContext context, F_skip skip, boolean before, Direction direction) {
        if (skip == null) {
            return;
        }
        int length = before ? skip.lenBefore() : skip.lenAfter();
        String expression = before ? skip.lenExprBefore() : skip.lenExprAfter();
        if (length == 0 && expression.isEmpty()) {
            return;
        }
        if (direction == Direction.PARSE) {
            ParseUtil.appendSkip_parse(length, expression, context);
        } else {
            ParseUtil.appendSkip_deParse(length, expression, context);
        }
    }

    private void appendFieldLogBefore(BuilderContext context, Direction direction, FieldLog<?> fieldLog) {
        if (fieldLog == null || loggingDisable(direction)) {
            return;
        }
        if (direction == Direction.PARSE) {
            fieldLog.buildParseBefore(context);
        } else {
            fieldLog.buildDeParseBefore(context);
        }
    }

    private void appendFieldLogAfter(BuilderContext context, Direction direction, FieldLog<?> fieldLog) {
        if (fieldLog == null || loggingDisable(direction)) {
            return;
        }
        if (direction == Direction.PARSE) {
            fieldLog.buildParseAfter(context);
        } else {
            fieldLog.buildDeParseAfter(context);
        }
    }

    private boolean loggingDisable(Direction direction) {
        return direction == Direction.PARSE ? !parseLogging : !deParseLogging;
    }

    private void buildFieldsWithClassSkip(BuilderContext context, C_skip skip, Direction direction) {
        int modelLength = ParseUtil.getClassByteLenIfPossible(modelClass);
        if (modelLength == -1) {
            appendDynamicClassSkip(context, skip, direction);
        } else {
            buildFields(context, direction);
            appendFixedClassSkip(context, skip, modelLength, direction);
        }
    }

    private void appendDynamicClassSkip(BuilderContext context, C_skip skip, Direction direction) {
        String indexMethod = direction == Direction.PARSE ? "readerIndex" : "writerIndex";
        ParseUtil.append(context.method_body, "final int {}={}.{}();\n",
                FieldBuilder.varNameStartIndex, FieldBuilder.varNameByteBuf, indexMethod);
        buildFields(context, direction);
        String lengthCode = skip.len() == 0
                ? ParseUtil.replaceExprToCode_class(skip.lenExpr(), context)
                : Integer.toString(skip.len());
        ParseUtil.append(context.method_body, "final int {}={}-{}.{}()+{};\n",
                FieldBuilder.varNameShouldSkip, lengthCode, FieldBuilder.varNameByteBuf,
                indexMethod, FieldBuilder.varNameStartIndex);
        ParseUtil.append(context.method_body, "if({}>0){\n", FieldBuilder.varNameShouldSkip);
        appendPadding(context.method_body, FieldBuilder.varNameShouldSkip, direction);
        appendClassSkipLog(context.method_body, FieldBuilder.varNameShouldSkip, direction);
        context.method_body.append("}\n");
    }

    private void appendFixedClassSkip(BuilderContext context, C_skip skip, int modelLength, Direction direction) {
        if (skip.len() == 0) {
            String lengthCode = direction == Direction.PARSE
                    ? ParseUtil.replaceExprToCode(skip.lenExpr(), context)
                    : ParseUtil.replaceExprToCode_class(skip.lenExpr(), context);
            String paddingCode = "(" + lengthCode + "-" + modelLength + ")";
            appendPadding(context.method_body, paddingCode, direction);
            appendClassSkipLog(context.method_body, paddingCode, direction);
            return;
        }
        int padding = skip.len() - modelLength;
        if (padding > 0) {
            String paddingCode = Integer.toString(padding);
            appendPadding(context.method_body, paddingCode, direction);
            appendClassSkipLog(context.method_body, paddingCode, direction);
        }
    }

    private static void appendPadding(StringBuilder body, String lengthCode, Direction direction) {
        String method = direction == Direction.PARSE ? "skipBytes" : "writeZero";
        ParseUtil.append(body, "{}.{}({});\n", FieldBuilder.varNameByteBuf, method, lengthCode);
    }

    private void appendClassSkipLog(StringBuilder body, String lengthCode, Direction direction) {
        if (loggingDisable(direction)) {
            return;
        }
        ParseUtil.append(body, "{}.{}({}.class,{});\n",
                ClassLog__C_skip.class.getName(), direction == Direction.PARSE ? "parse" : "deParse",
                modelClass.getName(), lengthCode);
    }

    private String buildSource(String packageName, String simpleClassName, String processBody, String deProcessBody) {
        StringBuilder source = new StringBuilder();
        ParseUtil.append(source, "package {};\n\n", packageName);
        ParseUtil.append(source, "public final class {} implements {}{\n", simpleClassName, Processor.class.getName());
        source.append(classFields);
        ParseUtil.append(source, "public {}(){\n", simpleClassName);
        source.append(constructorBody).append("}\n");
        ParseUtil.append(source, "@Override\npublic Object process(final {} {}, final {} {})",
                ByteBuf.class.getName(), FieldBuilder.varNameByteBuf,
                ProcessContext.class.getName(), FieldBuilder.varNameProcessContext);
        source.append(processBody).append('\n');
        ParseUtil.append(source, "@Override\npublic void deProcess(final {} {}, final {} {}, final Object $3)",
                ByteBuf.class.getName(), FieldBuilder.varNameByteBuf,
                ProcessContext.class.getName(), FieldBuilder.varNameProcessContext);
        return source.append(deProcessBody).append("\n}\n").toString();
    }

    private void logGeneratedParts(String processBody, String deProcessBody) {
        if (!printBuildLog) {
            return;
        }
        logger.info("\n----------clazz[{}] class field define body-------------\n{}\n", modelClass.getName(), classFields);
        logger.info("\n----------clazz[{}] constructor body-------------\n{{\n{}\n}}\n", modelClass.getName(), constructorBody);
        logger.info("\n-----------class[{}] process-----------{}\n", modelClass.getName(), processBody);
        logger.info("\n-----------class[{}] deProcess-----------{}\n", modelClass.getName(), deProcessBody);
    }

    private enum Direction {
        PARSE {
            @Override
            void build(FieldBuilder builder, BuilderContext context) {
                builder.buildParse(context);
            }
        },
        DE_PARSE {
            @Override
            void build(FieldBuilder builder, BuilderContext context) {
                builder.buildDeParse(context);
            }
        };

        abstract void build(FieldBuilder builder, BuilderContext context);
    }
}
