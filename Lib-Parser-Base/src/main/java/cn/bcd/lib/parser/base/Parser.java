package cn.bcd.lib.parser.base;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.anno.C_skip;
import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_array;
import cn.bcd.lib.parser.base.anno.F_skip;
import cn.bcd.lib.parser.base.builder.BuilderContext;
import cn.bcd.lib.parser.base.builder.FieldBuilder;
import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.DefaultNumValGetter;
import cn.bcd.lib.parser.base.data.NumValGetter;
import cn.bcd.lib.parser.base.log.LogCollector_deParse;
import cn.bcd.lib.parser.base.log.LogCollector_parse;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.base.util.DynamicProcessorCompiler;
import cn.bcd.lib.parser.base.util.ParseUtil;
import io.netty.buffer.AbstractByteBuf;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 解析器
 * 配合注解完成解析工作
 * 会扫描当前类和其父类的所有字段
 * 解析字段的顺序为 父类字段在子类之前
 * 字段必须符合如下规则
 * 1、public、非final、非static
 * 2、必须标注了如下注解之一
 * {@link cn.bcd.lib.parser.base.anno}下 F_ 开头的注解(F_skip除外)
 *
 * <p>
 * 工作原理:
 * 使用JDK编译器配合自定义注解、生成一套解析代码
 * 使用方法:
 * 1、首先获取类处理器
 * {@link #getProcessor(Class)}
 * {@link #getProcessor(Class, ByteOrder, NumValGetter)}
 * 2、调用解析或者反解析
 * <p>
 * 解析调用入口:
 * {@link Processor#process(ByteBuf, ProcessContext)}
 * <p>
 * 反解析调用入口:
 * {@link Processor#deProcess(ByteBuf, ProcessContext, Object)}
 * <p>
 * 性能表现:
 * 由于是字节码增强技术、和手动编写代码解析效率一样
 * <p>
 * 可配置方法
 * {@link #enableGenerateClassFile()} 生成class类文件、文件声称在{@link Processor}同目录下
 * {@link #enablePrintBuildLog()} 开启打印build日志
 * {@link #withDefaultLogCollector_parse()} 开启解析日志采集、此方法开启后会在解析程序中插入日志采集功能、降低程序性能、建议只在开发调试阶段开启
 * {@link #withDefaultLogCollector_deParse()} 开启反解析日志采集、此方法开启后会在反解析程序中插入日志采集功能、降低程序性能、建议只在开发调试阶段开启
 * <p>
 * 注意:
 * 如果启动了解析和反解析日志、并不是所有字段都会打印、逻辑参考
 * {@link ParseUtil#needLog(BuilderContext)}
 */
public class Parser {
    private final static Logger logger = LoggerFactory.getLogger(Parser.class);

    public final static Map<Class<? extends Annotation>, FieldBuilder> anno_fieldBuilder;

    static {
        anno_fieldBuilder = ParseUtil.getAllFieldBuild();
    }

    public final static Map<String, Processor<?>> beanProcessorKey_processor = new HashMap<>();
    /**
     * 解析log采集器
     * 需要注意的是、此功能用于调试、会在生成的class中加入日志代码、影响性能
     * 而且此功能开启时候避免多线程调用解析、会产生日志混淆、不易调试
     */
    public static LogCollector_parse logCollector_parse;
    public static LogCollector_deParse logCollector_deParse;
    /**
     * 是否在src/main/java下面生成class文件
     * 主要用于开发测试阶段、便于查看生成的结果
     */
    private static boolean generateClassFile = false;
    /**
     * 是否打印动态生成class的过程日志
     */
    private static boolean printBuildLog = false;

    /**
     * 禁用ByteBuf检查
     * 这样做会使性能提高10%~20%
     * 原因是在读取{@link ByteBuf}时候会进行如下两个检查
     * {@link AbstractByteBuf#checkAccessible} 可访问检查
     * {@link AbstractByteBuf#checkBounds} 边界检查
     */
    public static void disableByteBufCheck() {
        System.setProperty("io.netty.buffer.checkBounds", "false");
        System.setProperty("io.netty.buffer.checkAccessible", "false");
    }

    public static void withDefaultLogCollector_parse() {
        logCollector_parse = LogCollector_parse.defaultInstance;
    }

    public static void withDefaultLogCollector_deParse() {
        logCollector_deParse = LogCollector_deParse.defaultInstance;
    }

    public static void enablePrintBuildLog() {
        printBuildLog = true;
    }

    public static void enableGenerateClassFile() {
        generateClassFile = true;
    }

    private static void buildMethodBody_process(BuilderContext context) {
        final List<Field> fieldList = context.class_fieldList;
        if (fieldList.isEmpty()) {
            return;
        }
        for (int i = 0; i < fieldList.size(); i++) {
            Field field = fieldList.get(i);
            context.field = field;
            context.fieldIndex = i;
            boolean bitField = field.isAnnotationPresent(F_bit_num.class) || field.isAnnotationPresent(F_bit_num_array.class);
            F_skip f_skip = field.getAnnotation(F_skip.class);
            if (f_skip != null && (f_skip.lenBefore() != 0 || !f_skip.lenExprBefore().isEmpty())) {
                ParseUtil.appendSkip_parse(f_skip.lenBefore(), f_skip.lenExprBefore(), context);
            }
            if (logCollector_parse != null) {
                if (!bitField) {
                    ParseUtil.prependLogCode_parse(context);
                }
            }
            try {
                for (Map.Entry<Class<? extends Annotation>, FieldBuilder> entry : anno_fieldBuilder.entrySet()) {
                    Class<? extends Annotation> annoClass = entry.getKey();
                    if (field.isAnnotationPresent(annoClass)) {
                        entry.getValue().buildParse(context);
                    }
                }
            } finally {
                if (logCollector_parse != null) {
                    if (bitField) {
                        ParseUtil.appendBitLogCode_parse(context);
                    } else {
                        ParseUtil.appendLogCode_parse(context);
                    }
                }
            }
            if (f_skip != null && (f_skip.lenAfter() != 0 || !f_skip.lenExprAfter().isEmpty())) {
                ParseUtil.appendSkip_parse(f_skip.lenAfter(), f_skip.lenExprAfter(), context);
            }
        }

    }

    private static void buildMethodBody_deProcess(BuilderContext context) {
        final List<Field> fieldList = context.class_fieldList;
        if (fieldList.isEmpty()) {
            return;
        }
        for (int i = 0; i < fieldList.size(); i++) {
            Field field = fieldList.get(i);
            context.field = field;
            context.fieldIndex = i;
            boolean logBit = field.isAnnotationPresent(F_bit_num.class) || field.isAnnotationPresent(F_bit_num_array.class);
            F_skip f_skip = field.getAnnotation(F_skip.class);
            if (f_skip != null && (f_skip.lenBefore() != 0 || !f_skip.lenExprBefore().isEmpty())) {
                ParseUtil.appendSkip_deParse(f_skip.lenBefore(), f_skip.lenExprBefore(), context);
            }
            if (logCollector_deParse != null) {
                if (!logBit) {
                    ParseUtil.prependLogCode_deParse(context);
                }
            }
            try {
                for (Map.Entry<Class<? extends Annotation>, FieldBuilder> entry : anno_fieldBuilder.entrySet()) {
                    Class<? extends Annotation> annoClass = entry.getKey();
                    if (field.isAnnotationPresent(annoClass)) {
                        entry.getValue().buildDeParse(context);
                    }
                }
            } finally {
                if (logCollector_deParse != null) {
                    if (logBit) {
                        ParseUtil.appendBitLogCode_deParse(context);
                    } else {
                        ParseUtil.appendLogCode_deParse(context);
                    }
                }
            }
            if (f_skip != null && (f_skip.lenAfter() != 0 || !f_skip.lenExprAfter().isEmpty())) {
                ParseUtil.appendSkip_deParse(f_skip.lenAfter(), f_skip.lenExprAfter(), context);
            }
        }
    }

    private static <T> Class<T> buildClass(Class<T> clazz, ByteOrder byteOrder, NumValGetter numValGetter) {
        final String processor_class_name = Processor.class.getName();
        final String byteBufClassName = ByteBuf.class.getName();
        final String processContextClassName = ProcessContext.class.getName();
        final String clazzName = clazz.getName();

        String implProcessor_class_name = ParseUtil.getProcessorClassName(clazz, byteOrder, numValGetter);
        String packageName = implProcessor_class_name.substring(0, implProcessor_class_name.lastIndexOf('.'));
        String simpleClassName = implProcessor_class_name.substring(implProcessor_class_name.lastIndexOf('.') + 1);

        StringBuilder classFieldDefineBody = new StringBuilder();
        StringBuilder constructBody = new StringBuilder();
        final Map<String, String> classVarDefineToVarName = new HashMap<>();

        StringBuilder processBody = new StringBuilder();
        processBody.append("\n{\n");
        ParseUtil.append(processBody, "final {} {}=new {}();\n", clazzName, FieldBuilder.varNameInstance, clazzName);
        final List<Field> fieldList = ParseUtil.getParseFields(clazz);
        BuilderContext parseBuilderContext = new BuilderContext(classFieldDefineBody, constructBody, processBody, clazz, classVarDefineToVarName, byteOrder, fieldList, numValGetter);

        C_skip c_skip = clazz.getAnnotation(C_skip.class);
        if (c_skip == null) {
            buildMethodBody_process(parseBuilderContext);
        } else {
            int classByteLen = ParseUtil.getClassByteLenIfPossible(clazz);
            if (classByteLen == -1) {
                ParseUtil.append(processBody, "final int {}={}.readerIndex();\n", FieldBuilder.varNameStartIndex, FieldBuilder.varNameByteBuf);
                buildMethodBody_process(parseBuilderContext);
                String lenValCode;
                if (c_skip.len() == 0) {
                    lenValCode = ParseUtil.replaceExprToCode_class(c_skip.lenExpr(), parseBuilderContext);
                } else {
                    lenValCode = c_skip.len() + "";
                }
                ParseUtil.append(processBody, "final int {}={}-{}.readerIndex()+{};\n", FieldBuilder.varNameShouldSkip, lenValCode, FieldBuilder.varNameByteBuf, FieldBuilder.varNameStartIndex);
                ParseUtil.append(processBody, "if({}>0){\n", FieldBuilder.varNameShouldSkip);
                ParseUtil.append(processBody, "{}.skipBytes({});\n", FieldBuilder.varNameByteBuf, FieldBuilder.varNameShouldSkip);
                if (logCollector_parse != null) {
                    ParseUtil.append(processBody, "{}.logCollector_parse.collect_class({}.class,1,new Object[]{\"@C_skip skip[\"+{}+\"]\"});\n", Parser.class.getName(), clazzName, FieldBuilder.varNameShouldSkip);
                }
                ParseUtil.append(processBody, "}\n");
            } else {
                buildMethodBody_process(parseBuilderContext);
                if (c_skip.len() == 0) {
                    String lenValCode = ParseUtil.replaceExprToCode(c_skip.lenExpr(), parseBuilderContext);
                    String skipCode = "(" + lenValCode + "-" + classByteLen + ")";
                    ParseUtil.append(processBody, "{}.skipBytes({});\n", FieldBuilder.varNameByteBuf, skipCode);
                    if (logCollector_parse != null) {
                        ParseUtil.append(processBody, "{}.logCollector_parse.collect_class({}.class,1,new Object[]{\"@C_skip skip[\"+{}+\"]\"});\n", Parser.class.getName(), clazzName, skipCode);
                    }
                } else {
                    int skip = c_skip.len() - classByteLen;
                    if (skip > 0) {
                        ParseUtil.append(processBody, "{}.skipBytes({});\n", FieldBuilder.varNameByteBuf, skip);
                        if (logCollector_parse != null) {
                            ParseUtil.append(processBody, "{}.logCollector_parse.collect_class({}.class,1,new Object[]{\"@C_skip skip[{}]\"});\n", Parser.class.getName(), clazzName, skip);
                        }
                    }
                }
            }
        }
        ParseUtil.append(processBody, "return {};\n", FieldBuilder.varNameInstance);
        processBody.append("}");

        StringBuilder deProcessBody = new StringBuilder();
        deProcessBody.append("\n{\n");
        ParseUtil.append(deProcessBody, "final {} {}=({})$3;\n", clazzName, FieldBuilder.varNameInstance, clazzName);
        BuilderContext deParseBuilderContext = new BuilderContext(classFieldDefineBody, constructBody, deProcessBody, clazz, classVarDefineToVarName, byteOrder, fieldList, numValGetter);
        if (c_skip == null) {
            buildMethodBody_deProcess(deParseBuilderContext);
        } else {
            int classByteLen = ParseUtil.getClassByteLenIfPossible(clazz);
            if (classByteLen == -1) {
                ParseUtil.append(deProcessBody, "final int {}={}.writerIndex();\n", FieldBuilder.varNameStartIndex, FieldBuilder.varNameByteBuf);
                buildMethodBody_deProcess(deParseBuilderContext);
                String lenValCode;
                if (c_skip.len() == 0) {
                    lenValCode = ParseUtil.replaceExprToCode_class(c_skip.lenExpr(), deParseBuilderContext);
                } else {
                    lenValCode = c_skip.len() + "";
                }
                ParseUtil.append(deProcessBody, "final int {}={}-{}.writerIndex()+{};\n", FieldBuilder.varNameShouldSkip, lenValCode, FieldBuilder.varNameByteBuf, FieldBuilder.varNameStartIndex);
                ParseUtil.append(deProcessBody, "if({}>0){\n", FieldBuilder.varNameShouldSkip);
                ParseUtil.append(deProcessBody, "{}.writeZero({});\n", FieldBuilder.varNameByteBuf, FieldBuilder.varNameShouldSkip);
                if (logCollector_deParse != null) {
                    ParseUtil.append(deProcessBody, "{}.logCollector_deParse.collect_class({}.class,1,new Object[]{\"@C_skip append[\"+{}+\"]\"});\n", Parser.class.getName(), clazzName, FieldBuilder.varNameShouldSkip);
                }
                ParseUtil.append(deProcessBody, "}\n");
            } else {
                buildMethodBody_deProcess(deParseBuilderContext);
                if (c_skip.len() == 0) {
                    String lenValCode = ParseUtil.replaceExprToCode_class(c_skip.lenExpr(), deParseBuilderContext);
                    String skipCode = "(" + lenValCode + "-" + classByteLen + ")";
                    ParseUtil.append(deProcessBody, "{}.writeZero({});\n", FieldBuilder.varNameByteBuf, skipCode);
                    if (logCollector_deParse != null) {
                        ParseUtil.append(deProcessBody, "{}.logCollector_deParse.collect_class({}.class,1,new Object[]{\"@C_skip append[\"+{}+\"]\"});\n", Parser.class.getName(), clazzName, skipCode);
                    }
                } else {
                    int skip = c_skip.len() - classByteLen;
                    if (skip > 0) {
                        ParseUtil.append(deProcessBody, "{}.writeZero({});\n", FieldBuilder.varNameByteBuf, skip);
                        if (logCollector_deParse != null) {
                            ParseUtil.append(deProcessBody, "{}.logCollector_deParse.collect_class({}.class,1,new Object[]{\"@C_skip append[{}]\"});\n", Parser.class.getName(), clazzName, skip);
                        }
                    }
                }
            }
        }
        deProcessBody.append("}");

        if (printBuildLog) {
            logger.info("\n----------clazz[{}] class field define body-------------\n{}\n", clazz.getName(), classFieldDefineBody.toString());
            logger.info("\n----------clazz[{}] constructor body-------------\n{{\n{}\n}}\n", clazz.getName(), constructBody.toString());
            logger.info("\n-----------class[{}] process-----------{}\n", clazz.getName(), processBody.toString());
            logger.info("\n-----------class[{}] deProcess-----------{}\n", clazz.getName(), deProcessBody.toString());
        }

        StringBuilder source = new StringBuilder();
        ParseUtil.append(source, "package {};\n\n", packageName);
        ParseUtil.append(source, "public final class {} implements {}{\n", simpleClassName, processor_class_name);
        source.append(classFieldDefineBody);
        ParseUtil.append(source, "public {}(){\n", simpleClassName);
        source.append(constructBody);
        source.append("}\n");
        ParseUtil.append(source, "@Override\npublic Object process(final {} {}, final {} {})", byteBufClassName, FieldBuilder.varNameByteBuf, processContextClassName, FieldBuilder.varNameProcessContext);
        source.append(processBody).append("\n");
        ParseUtil.append(source, "@Override\npublic void deProcess(final {} {}, final {} {}, final Object $3)", byteBufClassName, FieldBuilder.varNameByteBuf, processContextClassName, FieldBuilder.varNameProcessContext);
        source.append(deProcessBody).append("\n");
        source.append("}\n");

        if (printBuildLog) {
            logger.info("\n-----------class[{}] source-----------\n{}\n", clazz.getName(), source.toString());
        }
        return (Class<T>) DynamicProcessorCompiler.compileAndDefine(implProcessor_class_name, source.toString(), generateClassFile);
    }

    /**
     * 获取类解析器
     * 使用默认字节序模式和位模式
     *
     * @param clazz 实体类类型
     * @param <T>
     * @return
     */
    public static <T> Processor<T> getProcessor(Class<T> clazz) {
        return getProcessor(clazz, ByteOrder.Default, DefaultNumValGetter.instance);
    }

    /**
     * 获取类解析器
     *
     * @param clazz        实体类类型
     * @param byteOrder    实体类字节码实现 字节序模式
     * @param numValGetter 数值字段获取器、可以为null
     * @param <T>
     * @return
     */
    public static <T> Processor<T> getProcessor(Class<T> clazz, ByteOrder byteOrder, NumValGetter numValGetter) {
        final String key = ParseUtil.getProcessorKey(clazz, byteOrder, numValGetter);
        Processor<T> processor = (Processor<T>) beanProcessorKey_processor.get(key);
        if (processor == null) {
            synchronized (beanProcessorKey_processor) {
                processor = (Processor<T>) beanProcessorKey_processor.get(key);
                if (processor == null) {
                    try {
                        final Class<T> processClass = Parser.buildClass(clazz, byteOrder, numValGetter);
                        processor = (Processor<T>) processClass.getConstructor().newInstance();
                        beanProcessorKey_processor.put(key, processor);
                    } catch (Exception ex) {
                        throw BaseException.get(ex);
                    }
                }
            }
        }
        return processor;
    }
}
