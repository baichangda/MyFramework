package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.anno.F_bit_num_array;
import cn.bcd.lib.parser.base.builder.BuilderContext;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FieldLog__F_bit_num_array extends FieldLog<F_bit_num_array> {
    static Logger logger = LoggerFactory.getLogger(FieldLog__F_bit_num_array.class);

    public FieldLog__F_bit_num_array() {
        super(F_bit_num_array.class);
    }

    @Override
    public void buildParseAfter(BuilderContext context) {
        F_bit_num_array annotation = context.field.getAnnotation(F_bit_num_array.class);
        appendParseLogCall(context, valueCode(context), Boolean.toString(annotation.skip()), bitLogsCode(context, true));
    }

    @Override
    public void buildDeParseAfter(BuilderContext context) {
        F_bit_num_array annotation = context.field.getAnnotation(F_bit_num_array.class);
        appendDeParseLogCall(context, valueCode(context), Boolean.toString(annotation.skip()), bitLogsCode(context, false));
    }

    /**
     * 输出 @F_bit_num_array 字段的解析日志；每次字段解析只打印一行。
     *
     * @param clazz        字段所属的模型类
     * @param fieldName    字段名称
     * @param content      解析该字段时消费的原始字节
     * @param value        解析后的字段值
     * @param skipped      是否因注解的 skip=true 跳过字段值解析
     * @param bitLogs      解析过程产生的位读取明细
     */
    public static void parse(Class<?> clazz, String fieldName, byte[] content,
                             Object value, boolean skipped, BitBuf_reader_log.Log[] bitLogs) {
        String location = LogUtil.getFieldLocation(clazz, fieldName);
        String hex = ByteBufUtil.hexDump(content).toUpperCase();
        String detail = LogUtil.formatBitLogs(bitLogs);
        if (skipped) {
            logger.info("--parse @F_bit_num_array skip field{}--[{}.{}] hex[{}] bit[{}]", location, clazz.getSimpleName(), fieldName, hex, detail);
        } else {
            logger.info("--parse @F_bit_num_array field{}--[{}.{}] hex[{}] val{} bit[{}]", location, clazz.getSimpleName(),
                    fieldName, hex, LogUtil.formatLogValue(value), detail);
        }
    }

    /**
     * 输出 @F_bit_num_array 字段的反解析日志；每次字段反解析只打印一行。
     *
     * @param clazz        字段所属的模型类
     * @param fieldName    字段名称
     * @param content      反解析该字段时写入的原始字节
     * @param value        参与反解析的字段值
     * @param skipped      是否因注解的 skip=true 跳过字段值写入
     * @param bitLogs      反解析过程产生的位写入明细
     */
    public static void deParse(Class<?> clazz, String fieldName, byte[] content,
                               Object value, boolean skipped, BitBuf_writer_log.Log[] bitLogs) {
        String location = LogUtil.getFieldLocation(clazz, fieldName);
        String hex = ByteBufUtil.hexDump(content).toUpperCase();
        String detail = LogUtil.formatBitLogs(bitLogs);
        if (skipped) {
            logger.info("--deParse @F_bit_num_array skip field{}--[{}.{}] hex[{}] bit[{}]", location, clazz.getSimpleName(), fieldName, hex, detail);
        } else {
            logger.info("--deParse @F_bit_num_array field{}--[{}.{}] val{} hex[{}] bit[{}]", location, clazz.getSimpleName(),
                    fieldName, LogUtil.formatLogValue(value), hex, detail);
        }
    }
}
