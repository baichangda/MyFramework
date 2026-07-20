package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.C_skip;
import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_array;
import cn.bcd.lib.parser.base.anno.F_skip;
import cn.bcd.lib.parser.base.anno.F_var;
import cn.bcd.lib.parser.base.builder.BuilderContext;
import cn.bcd.lib.parser.base.builder.FieldBuilder;
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

        ParseUtil.append(body, "final Object previousParent={}.enter({});\ntry{\n",
                FieldBuilder.varNameProcessContext, FieldBuilder.varNameInstance);

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
        ParseUtil.append(body, "}finally{{}.exit(previousParent);}\n", FieldBuilder.varNameProcessContext);
        return body.append('}').toString();
    }

    private void buildFields(BuilderContext context, Direction direction) {
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            context.field = field;
            context.fieldIndex = i;
            F_skip skip = field.getAnnotation(F_skip.class);
            appendFieldSkip(context, skip, true, direction);
            appendFieldLogBefore(context, direction);
            try {
                appendFieldVar(context, direction, true);
                direction.build(findFieldBuilder(field), context);
                appendFieldVar(context, direction, false);
            } finally {
                appendFieldLogAfter(context, direction);
            }
            appendFieldSkip(context, skip, false, direction);
        }
    }

    private static void appendFieldVar(BuilderContext context, Direction direction, boolean before) {
        F_var annotation = context.field.getAnnotation(F_var.class);
        if (annotation == null || before != (direction == Direction.DE_PARSE)) {
            return;
        }
        String fieldValue = FieldBuilder.varNameInstance + "." + context.field.getName();
        ParseUtil.append(context.method_body, "{}.putVar({},{});\n",
                FieldBuilder.varNameProcessContext, annotation.index(),
                ParseUtil.boxing(fieldValue, context.field.getType()));
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

    private void appendFieldLogBefore(BuilderContext context, Direction direction) {
        if (loggingDisable(direction) || isBitField(context.field)) {
            return;
        }
        if (direction == Direction.PARSE) {
            ParseUtil.prependLogCode_parse(context);
        } else {
            ParseUtil.prependLogCode_deParse(context);
        }
    }

    private void appendFieldLogAfter(BuilderContext context, Direction direction) {
        if (loggingDisable(direction)) {
            return;
        }
        boolean bitField = isBitField(context.field);
        if (direction == Direction.PARSE) {
            if (bitField) {
                ParseUtil.appendBitLogCode_parse(context);
            } else {
                ParseUtil.appendLogCode_parse(context);
            }
        } else if (bitField) {
            ParseUtil.appendBitLogCode_deParse(context);
        } else {
            ParseUtil.appendLogCode_deParse(context);
        }
    }

    private boolean loggingDisable(Direction direction) {
        return direction == Direction.PARSE ? !parseLogging : !deParseLogging;
    }

    private static boolean isBitField(Field field) {
        return field.isAnnotationPresent(F_bit_num.class) || field.isAnnotationPresent(F_bit_num_array.class);
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
        appendClassSkipLog(context.method_body, FieldBuilder.varNameShouldSkip, true, direction);
        context.method_body.append("}\n");
    }

    private void appendFixedClassSkip(BuilderContext context, C_skip skip, int modelLength, Direction direction) {
        if (skip.len() == 0) {
            String lengthCode = direction == Direction.PARSE
                    ? ParseUtil.replaceExprToCode(skip.lenExpr(), context)
                    : ParseUtil.replaceExprToCode_class(skip.lenExpr(), context);
            String paddingCode = "(" + lengthCode + "-" + modelLength + ")";
            appendPadding(context.method_body, paddingCode, direction);
            appendClassSkipLog(context.method_body, paddingCode, true, direction);
            return;
        }
        int padding = skip.len() - modelLength;
        if (padding > 0) {
            String paddingCode = Integer.toString(padding);
            appendPadding(context.method_body, paddingCode, direction);
            appendClassSkipLog(context.method_body, paddingCode, false, direction);
        }
    }

    private static void appendPadding(StringBuilder body, String lengthCode, Direction direction) {
        String method = direction == Direction.PARSE ? "skipBytes" : "writeZero";
        ParseUtil.append(body, "{}.{}({});\n", FieldBuilder.varNameByteBuf, method, lengthCode);
    }

    private void appendClassSkipLog(StringBuilder body, String lengthCode, boolean expression, Direction direction) {
        if (loggingDisable(direction)) {
            return;
        }
        String collector = direction == Direction.PARSE ? "parseLogCollector" : "deParseLogCollector";
        String action = direction == Direction.PARSE ? "skip" : "append";
        if (expression) {
            ParseUtil.append(body, "{}.{}().collect_class({}.class,1,new Object[]{\"@C_skip {}[\"+{}+\"]\"});\n",
                    Parser.class.getName(), collector, modelClass.getName(), action, lengthCode);
        } else {
            ParseUtil.append(body, "{}.{}().collect_class({}.class,1,new Object[]{\"@C_skip {}[{}]\"});\n",
                    Parser.class.getName(), collector, modelClass.getName(), action, lengthCode);
        }
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
