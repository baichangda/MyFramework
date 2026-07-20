package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.anno.F_string;
import cn.bcd.lib.parser.base.builder.BuilderContext;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FieldLog__F_string extends FieldLog<F_string> {
    static Logger logger = LoggerFactory.getLogger(FieldLog__F_string.class);

    public FieldLog__F_string() {
        super(F_string.class);
    }

    @Override
    public void buildParseAfter(BuilderContext context) {
        F_string annotation = context.field.getAnnotation(F_string.class);
        appendParseLogCall(context, valueCode(context), Boolean.toString(annotation.skip()));
    }

    @Override
    public void buildDeParseAfter(BuilderContext context) {
        F_string annotation = context.field.getAnnotation(F_string.class);
        appendDeParseLogCall(context, valueCode(context), Boolean.toString(annotation.skip()));
    }

    /**
     * 输出 @F_string 字段的解析日志；每次字段解析只打印一行。
     *
     * @param clazz        字段所属的模型类
     * @param fieldName    字段名称
     * @param content      解析该字段时消费的原始字节
     * @param value        解析后的字段值
     * @param skipped      是否因注解的 skip=true 跳过字段值解析
     */
    public static void parse(Class<?> clazz, String fieldName, byte[] content,
                             String value, boolean skipped) {
        String location = LogUtil.getFieldLocation(clazz, fieldName);
        String hex = ByteBufUtil.hexDump(content).toUpperCase();
        if (skipped) {
            logger.info("--parse @F_string skip field{}--[{}.{}] hex[{}]", location, clazz.getSimpleName(), fieldName, hex);
        } else {
            logger.info("--parse @F_string field{}--[{}.{}] hex[{}] val{}", location, clazz.getSimpleName(),
                    fieldName, hex, LogUtil.formatLogValue(value));
        }
    }

    /**
     * 输出 @F_string 字段的反解析日志；每次字段反解析只打印一行。
     *
     * @param clazz        字段所属的模型类
     * @param fieldName    字段名称
     * @param content      反解析该字段时写入的原始字节
     * @param value        参与反解析的字段值
     * @param skipped      是否因注解的 skip=true 跳过字段值写入
     */
    public static void deParse(Class<?> clazz, String fieldName, byte[] content,
                               String value, boolean skipped) {
        String location = LogUtil.getFieldLocation(clazz, fieldName);
        String hex = ByteBufUtil.hexDump(content).toUpperCase();
        if (skipped) {
            logger.info("--deParse @F_string skip field{}--[{}.{}] hex[{}]", location, clazz.getSimpleName(), fieldName, hex);
        } else {
            logger.info("--deParse @F_string field{}--[{}.{}] val{} hex[{}]", location, clazz.getSimpleName(),
                    fieldName, LogUtil.formatLogValue(value), hex);
        }
    }
}
