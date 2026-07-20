package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.builder.BuilderContext;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FieldLog__F_num extends FieldLog<F_num> {
    static Logger logger = LoggerFactory.getLogger(FieldLog__F_num.class);

    public FieldLog__F_num() {
        super(F_num.class);
    }

    @Override
    public void buildParseAfter(BuilderContext context) {
        F_num annotation = context.field.getAnnotation(F_num.class);
        String valueTypeCode = annotation.checkVal() ? companionValueCode(context) : "null";
        appendParseLogCall(context, valueCode(context), valueTypeCode, Boolean.toString(annotation.skip()));
    }

    @Override
    public void buildDeParseAfter(BuilderContext context) {
        F_num annotation = context.field.getAnnotation(F_num.class);
        String valueTypeCode = annotation.checkVal() ? companionValueCode(context) : "null";
        appendDeParseLogCall(context, valueCode(context), valueTypeCode, Boolean.toString(annotation.skip()));
    }

    /**
     * 输出 @F_num 字段的解析日志；每次字段解析只打印一行。
     *
     * @param clazz        字段所属的模型类
     * @param fieldName    字段名称
     * @param content      解析该字段时消费的原始字节
     * @param value        解析后的字段值
     * @param valueType    同名 __v 伴生字段值；未启用值校验时为 null
     * @param skipped      是否因注解的 skip=true 跳过字段值解析
     */
    public static void parse(Class<?> clazz, String fieldName, byte[] content,
                             Object value, Byte valueType, boolean skipped) {
        String location = LogUtil.getFieldLocation(clazz, fieldName);
        String hex = ByteBufUtil.hexDump(content).toUpperCase();
        if (skipped) {
            logger.info("--parse @F_num skip field{}--[{}.{}] hex[{}]", location, clazz.getSimpleName(), fieldName, hex);
        } else if (valueType != null) {
            logger.info("--parse @F_num field{}--[{}.{}] hex[{}] val{} {}{}", location, clazz.getSimpleName(),
                    fieldName, hex, LogUtil.formatLogValue(value), fieldName + "__v", LogUtil.formatLogValue(valueType));
        } else {
            logger.info("--parse @F_num field{}--[{}.{}] hex[{}] val{}", location, clazz.getSimpleName(),
                    fieldName, hex, LogUtil.formatLogValue(value));
        }
    }

    /**
     * 输出 @F_num 字段的反解析日志；每次字段反解析只打印一行。
     *
     * @param clazz        字段所属的模型类
     * @param fieldName    字段名称
     * @param content      反解析该字段时写入的原始字节
     * @param value        参与反解析的字段值
     * @param valueType    同名 __v 伴生字段值；未启用值校验时为 null
     * @param skipped      是否因注解的 skip=true 跳过字段值写入
     */
    public static void deParse(Class<?> clazz, String fieldName, byte[] content,
                               Object value, Byte valueType, boolean skipped) {
        String location = LogUtil.getFieldLocation(clazz, fieldName);
        String hex = ByteBufUtil.hexDump(content).toUpperCase();
        if (skipped) {
            logger.info("--deParse @F_num skip field{}--[{}.{}] hex[{}]", location, clazz.getSimpleName(), fieldName, hex);
        } else if (valueType != null) {
            logger.info("--deParse @F_num field{}--[{}.{}] val{} {}{} hex[{}]", location, clazz.getSimpleName(),
                    fieldName, LogUtil.formatLogValue(value), fieldName + "__v", LogUtil.formatLogValue(valueType), hex);
        } else {
            logger.info("--deParse @F_num field{}--[{}.{}] val{} hex[{}]", location, clazz.getSimpleName(),
                    fieldName, LogUtil.formatLogValue(value), hex);
        }
    }
}
