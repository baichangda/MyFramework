package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.util.BitBuf_writer_log;
import cn.bcd.lib.parser.base.util.LogUtil;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public class DefaultLogCollector_deParse implements LogCollector_deParse {

    static Logger logger = LoggerFactory.getLogger(DefaultLogCollector_deParse.class);

    @Override
    public void collect_class(Class<?> clazz, int type, Object... args) {
        switch (type) {
            case 1 -> {
                logger.info("--deParse skip class{} {}", LogUtil.getFieldStackTrace(clazz, null), args[0]);
            }
            default -> {
            }
        }
    }

    /**
     * @param clazz
     * @param fieldName
     * @param type      1：F_skip
     *                  2、F_bit_num
     *                  0、other
     * @param args
     */
    @Override
    public void collect_field(Class<?> clazz, String fieldName, int type, Object... args) {
        try {
            Field field = clazz.getField(fieldName);
            Class<?> fieldDeclaringClass = field.getDeclaringClass();
            switch (type) {
                case 1 -> {
                    byte[] content = (byte[]) args[0];
                    logger.info("--deParse skip field{}--[{}.{}] append len[{}] [{}]"
                            , LogUtil.getFieldStackTrace(fieldDeclaringClass, fieldName)
                            , clazz.getSimpleName()
                            , fieldName
                            , content.length
                            , ByteBufUtil.hexDump(content).toUpperCase());
                }
                case 2 -> {
                    Object val = args[0];
                    BitBuf_writer_log.Log[] logs = (BitBuf_writer_log.Log[]) args[1];
                    for (BitBuf_writer_log.Log log : logs) {
                        logger.info("--deParse bit field{}--[{}.{}] val[{}] {}"
                                , LogUtil.getFieldStackTrace(fieldDeclaringClass, fieldName)
                                , clazz.getSimpleName()
                                , fieldName
                                , val
                                , log.msg());
                    }
                }
                case 3 -> {
                    Object val = args[0];
                    byte[] content = (byte[]) args[1];
                    Object valTypeContent = args[2];
                    logger.info("--deParse field{}--[{}.{}] {}[{}] {}[{}]->[{}]"
                            , LogUtil.getFieldStackTrace(fieldDeclaringClass, fieldName)
                            , clazz.getSimpleName()
                            , fieldName
                            , fieldName + "__v"
                            , valTypeContent
                            , fieldName
                            , val
                            , ByteBufUtil.hexDump(content).toUpperCase()
                    );
                }
                default -> {
                    Object val = args[0];
                    byte[] content = (byte[]) args[1];
                    logger.info("--deParse field{}--[{}.{}] {}[{}]->[{}]"
                            , LogUtil.getFieldStackTrace(fieldDeclaringClass, fieldName)
                            , clazz.getSimpleName()
                            , fieldName
                            , fieldName
                            , val
                            , ByteBufUtil.hexDump(content).toUpperCase());
                }
            }
        } catch (NoSuchFieldException e) {
            throw BaseException.get(e);
        }
    }
}
