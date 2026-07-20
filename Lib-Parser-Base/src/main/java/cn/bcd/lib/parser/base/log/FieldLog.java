package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.builder.BuilderContext;
import cn.bcd.lib.parser.base.builder.FieldBuilder;
import cn.bcd.lib.parser.base.util.ParseUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * 字段注解日志代码生成器。
 * <p>
 * 每一种参与解析的字段注解都对应一个 {@code FieldLog__F_xxx} 实现，并通过
 * {@link FieldLogRegistry} 注册。本类负责在动态生成的解析、反解析代码中记录
 * ByteBuf 起始位置、截取字段占用的字节，并调用具体实现类的日志方法；日志格式
 * 和最终打印行为由各注解对应的实现类负责。
 * </p>
 *
 * @param <A> 当前日志实现对应的字段注解类型
 */
public abstract class FieldLog<A extends Annotation> {
    private final Class<A> annotationClass;

    /**
     * @param annotationClass 当前日志实现对应的字段注解类型
     */
    protected FieldLog(Class<A> annotationClass) {
        this.annotationClass = annotationClass;
    }

    /**
     * 返回当前日志实现对应的字段注解，用于注册和查找日志生成器。
     */
    public final Class<A> annotationClass() {
        return annotationClass;
    }

    /**
     * 在字段解析代码之前记录 ByteBuf 的 readerIndex。
     */
    public final void buildParseBefore(BuilderContext context) {
        appendBefore(context, true);
    }

    /**
     * 在字段反解析代码之前记录 ByteBuf 的 writerIndex。
     */
    public final void buildDeParseBefore(BuilderContext context) {
        appendBefore(context, false);
    }

    /**
     * 生成字段解析完成后的日志调用代码。
     */
    public abstract void buildParseAfter(BuilderContext context);

    /**
     * 生成字段反解析完成后的日志调用代码。
     */
    public abstract void buildDeParseAfter(BuilderContext context);

    /**
     * 生成解析日志方法调用。各实现类只传入自身实际需要的参数代码。
     */
    protected final void appendParseLogCall(BuilderContext context, String... argumentCodes) {
        appendLogCall(context, true, argumentCodes);
    }

    /**
     * 生成反解析日志方法调用。各实现类只传入自身实际需要的参数代码。
     */
    protected final void appendDeParseLogCall(BuilderContext context, String... argumentCodes) {
        appendLogCall(context, false, argumentCodes);
    }

    private void appendBefore(BuilderContext context, boolean parse) {
        String indexMethod = parse ? "readerIndex" : "writerIndex";
        ParseUtil.append(context.method_body, "final int {}={}.{}();\n",
                indexName(context, parse), FieldBuilder.varNameByteBuf, indexMethod);
    }

    private void appendLogCall(BuilderContext context, boolean parse, String... argumentCodes) {
        String bytesName = appendCapturedBytes(context, parse);
        ParseUtil.append(context.method_body, "{}.{}({}.class,\"{}\",{}",
                getClass().getName(), parse ? "parse" : "deParse",
                context.clazz.getName(), context.field.getName(), bytesName);
        for (String argumentCode : argumentCodes) {
            ParseUtil.append(context.method_body, ",{}", argumentCode);
        }
        context.method_body.append(");\n");
    }

    /**
     * 返回生成代码中的字段值表达式，并在字段为基本类型时完成装箱。
     */
    protected final String valueCode(BuilderContext context) {
        return ParseUtil.boxing(FieldBuilder.varNameInstance + "." + context.field.getName(), context.field.getType());
    }

    /**
     * 返回生成代码中的 {@code __v} 伴生字段值表达式。
     */
    protected final String companionValueCode(BuilderContext context) {
        try {
            Field companion = context.clazz.getField(context.field.getName() + "__v");
            return ParseUtil.boxing(FieldBuilder.varNameInstance + "." + companion.getName(), companion.getType());
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 返回生成代码中的位操作日志数组表达式。
     */
    protected final String bitLogsCode(BuilderContext context, boolean parse) {
        String bitBuf = parse ? context.getBitBuf_parse() : context.getBitBuf_deParse();
        return bitBuf + ".takeLogs()";
    }

    /**
     * 根据字段处理前后的 ByteBuf 索引生成字节截取代码，并返回生成代码中的字节数组变量名。
     */
    protected final String appendCapturedBytes(BuilderContext context, boolean parse) {
        String indexMethod = parse ? "readerIndex" : "writerIndex";
        String bytesName = bytesName(context);
        ParseUtil.append(context.method_body, "final byte[] {}=new byte[{}.{}()-{}];\n",
                bytesName, FieldBuilder.varNameByteBuf, indexMethod, indexName(context, parse));
        ParseUtil.append(context.method_body, "{}.getBytes({},{});\n",
                FieldBuilder.varNameByteBuf, indexName(context, parse), bytesName);
        return bytesName;
    }

    private String indexName(BuilderContext context, boolean parse) {
        return ParseUtil.getFieldVarName(context) + "_log_" + annotationClass.getSimpleName()
                + (parse ? "_readerIndex" : "_writerIndex");
    }

    private String bytesName(BuilderContext context) {
        return ParseUtil.getFieldVarName(context) + "_log_" + annotationClass.getSimpleName() + "_bytes";
    }
}
