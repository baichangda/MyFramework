package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.anno.F_bean_list;
import cn.bcd.lib.parser.base.builder.BuilderContext;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FieldLog__F_bean_list extends FieldLog<F_bean_list> {
    static Logger logger = LoggerFactory.getLogger(FieldLog__F_bean_list.class);

    public FieldLog__F_bean_list() {
        super(F_bean_list.class);
    }

    @Override
    public void buildParseAfter(BuilderContext context) {
        appendParseLogCall(context, valueCode(context));
    }

    @Override
    public void buildDeParseAfter(BuilderContext context) {
        appendDeParseLogCall(context, valueCode(context));
    }

    /**
     * 输出 @F_bean_list 字段的解析日志；每次字段解析只打印一行。
     *
     * @param clazz        字段所属的模型类
     * @param fieldName    字段名称
     * @param content      解析该字段时消费的原始字节
     * @param value        解析后的对象数组或 {@link java.util.List} 字段值
     */
    public static void parse(Class<?> clazz, String fieldName, byte[] content,
                             Object value) {
        String location = LogUtil.getFieldLocation(clazz, fieldName);
        String hex = ByteBufUtil.hexDump(content).toUpperCase();
        logger.info("--parse @F_bean_list field{}--[{}.{}] hex[{}] val{}", location, clazz.getSimpleName(),
                fieldName, hex, LogUtil.formatLogValue(value));
    }

    /**
     * 输出 @F_bean_list 字段的反解析日志；每次字段反解析只打印一行。
     *
     * @param clazz        字段所属的模型类
     * @param fieldName    字段名称
     * @param content      反解析该字段时写入的原始字节
     * @param value        参与反解析的对象数组或 {@link java.util.List} 字段值
     */
    public static void deParse(Class<?> clazz, String fieldName, byte[] content,
                               Object value) {
        String location = LogUtil.getFieldLocation(clazz, fieldName);
        String hex = ByteBufUtil.hexDump(content).toUpperCase();
        logger.info("--deParse @F_bean_list field{}--[{}.{}] val{} hex[{}]", location, clazz.getSimpleName(),
                fieldName, LogUtil.formatLogValue(value), hex);
    }
}
