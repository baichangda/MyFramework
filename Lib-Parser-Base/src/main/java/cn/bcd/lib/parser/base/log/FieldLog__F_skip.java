package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.anno.F_skip;
import cn.bcd.lib.parser.base.builder.BuilderContext;
import cn.bcd.lib.parser.base.util.ParseUtil;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public final class FieldLog__F_skip extends FieldLog<F_skip> {
    static Logger logger = LoggerFactory.getLogger(FieldLog__F_skip.class);

    public FieldLog__F_skip() {
        super(F_skip.class);
    }

    @Override
    public void buildParseAfter(BuilderContext context) {
        appendAfter(context, true);
    }

    @Override
    public void buildDeParseAfter(BuilderContext context) {
        appendAfter(context, false);
    }

    private void appendAfter(BuilderContext context, boolean parse) {
        F_skip annotation = context.field.getAnnotation(F_skip.class);
        String bytesName = appendCapturedBytes(context, parse);
        String beforeLength = lengthCode(context, annotation.lenBefore(), annotation.lenExprBefore());
        String afterLength = lengthCode(context, annotation.lenAfter(), annotation.lenExprAfter());
        ParseUtil.append(context.method_body, "{}.{}({}.class,\"{}\",{},{},{});\n",
                FieldLog__F_skip.class.getName(), parse ? "parse" : "deParse",
                context.clazz.getName(), context.field.getName(), bytesName, beforeLength, afterLength);
    }

    private static String lengthCode(BuilderContext context, int length, String expression) {
        if (length != 0) {
            return Integer.toString(length);
        }
        return expression.isEmpty() ? "0" : ParseUtil.replaceExprToCode(expression, context);
    }

    /**
     * 输出 {@code @F_skip} 的解析日志，将字段前后跳过的字节合并在同一行中。
     *
     * @param clazz        字段所属的模型类
     * @param fieldName    字段名称
     * @param content      包含前置 skip、字段内容和后置 skip 的完整字节
     * @param beforeLength 前置 skip 的字节数
     * @param afterLength  后置 skip 的字节数
     */
    public static void parse(Class<?> clazz, String fieldName, byte[] content, int beforeLength, int afterLength) {
        log("parse", clazz, fieldName, content, beforeLength, afterLength);
    }

    /**
     * 输出 {@code @F_skip} 的反解析日志，将字段前后补写的字节合并在同一行中。
     *
     * @param clazz        字段所属的模型类
     * @param fieldName    字段名称
     * @param content      包含前置 skip、字段内容和后置 skip 的完整字节
     * @param beforeLength 前置 skip 的字节数
     * @param afterLength  后置 skip 的字节数
     */
    public static void deParse(Class<?> clazz, String fieldName, byte[] content, int beforeLength, int afterLength) {
        log("deParse", clazz, fieldName, content, beforeLength, afterLength);
    }

    private static void log(String direction, Class<?> clazz, String fieldName, byte[] content,
                            int beforeLength, int afterLength) {
        byte[] before = Arrays.copyOfRange(content, 0, beforeLength);
        byte[] after = Arrays.copyOfRange(content, content.length - afterLength, content.length);
        logger.info("--{} @F_skip field{}--[{}.{}] before[{}] after[{}]",
                direction, LogUtil.getFieldLocation(clazz, fieldName), clazz.getSimpleName(), fieldName,
                ByteBufUtil.hexDump(before).toUpperCase(), ByteBufUtil.hexDump(after).toUpperCase());
    }
}
