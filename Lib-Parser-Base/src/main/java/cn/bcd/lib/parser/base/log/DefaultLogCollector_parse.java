package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.util.BitBuf_reader_log;
import cn.bcd.lib.parser.base.util.LogUtil;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public class DefaultLogCollector_parse implements LogCollector_parse {
    static Logger logger = LoggerFactory.getLogger(DefaultLogCollector_parse.class);


    public void collect_class(Class<?> clazz, int type, Object... args) {
        switch (type) {
            case 1 -> {
                logger.info("--parse skip class{} {}", LogUtil.getFieldStackTrace(clazz, null), args[0]);
            }
            default -> {
            }
        }
    }


    @Override
    public void collect_field(Class<?> clazz, String fieldName, int type, Object... args) {
        try {
            Field field = clazz.getField(fieldName);
            Class<?> fieldDeclaringClass = field.getDeclaringClass();
            switch (type) {
                case 1 -> {
                    byte[] content = (byte[]) args[0];
                    logger.info("--parse skip field{}--[{}.{}] skip len[{}] hex[{}]"
                            , LogUtil.getFieldStackTrace(fieldDeclaringClass, fieldName)
                            , clazz.getSimpleName()
                            , fieldName
                            , content.length
                            , ByteBufUtil.hexDump(content).toUpperCase()
                    );
                }
                case 2 -> {
                    BitBuf_reader_log.Log[] logs = (BitBuf_reader_log.Log[]) args[0];
                    Object val = args[1];
                    for (BitBuf_reader_log.Log log : logs) {
                        logger.info("--parse bit field{}--[{}.{}] val[{}] {}"
                                , LogUtil.getFieldStackTrace(fieldDeclaringClass, fieldName)
                                , clazz.getSimpleName()
                                , fieldName
                                , val
                                , log.msg());
                    }
                }
                case 3 -> {
                    byte[] content = (byte[]) args[0];
                    Object val = args[1];
                    Object valTypeContent = args[2];
                    logger.info("--parse field{}--[{}.{}] {}[{}] {}[{}]->[{}]"
                            , LogUtil.getFieldStackTrace(fieldDeclaringClass, fieldName)
                            , clazz.getSimpleName()
                            , fieldName
                            , fieldName + "__v"
                            , valTypeContent
                            , fieldName
                            , ByteBufUtil.hexDump(content).toUpperCase()
                            , val
                    );
                }
                default -> {
                    byte[] content = (byte[]) args[0];
                    Object val = args[1];
                    logger.info("--parse field{}--[{}.{}] {}[{}]->[{}]"
                            , LogUtil.getFieldStackTrace(fieldDeclaringClass, fieldName)
                            , clazz.getSimpleName()
                            , fieldName
                            , fieldName
                            , ByteBufUtil.hexDump(content).toUpperCase()
                            , val
                    );
                }
            }
        } catch (NoSuchFieldException e) {
            throw BaseException.get(e);
        }
    }
}
