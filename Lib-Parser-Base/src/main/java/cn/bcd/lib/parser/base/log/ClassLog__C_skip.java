package cn.bcd.lib.parser.base.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClassLog__C_skip {
    private static final Logger logger = LoggerFactory.getLogger(ClassLog__C_skip.class);

    private ClassLog__C_skip() {
    }

    /**
     * 输出 {@code @C_skip} 的解析日志，记录整个模型跳过的字节数。
     *
     * @param clazz  被跳过的模型类
     * @param length 解析时跳过的字节数
     */
    public static void parse(Class<?> clazz, int length) {
        logger.info("--parse @C_skip class{}--[{}] skip[{}]",
                LogUtil.getFieldStackTrace(clazz, null), clazz.getName(), length);
    }

    /**
     * 输出 {@code @C_skip} 的反解析日志，记录整个模型补写的字节数。
     *
     * @param clazz  被跳过的模型类
     * @param length 反解析时补写的字节数
     */
    public static void deParse(Class<?> clazz, int length) {
        logger.info("--deParse @C_skip class{}--[{}] append[{}]",
                LogUtil.getFieldStackTrace(clazz, null), clazz.getName(), length);
    }
}
