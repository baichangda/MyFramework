package cn.bcd.lib.parser.base;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.builder.FieldBuilder;
import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.DefaultNumValGetter;
import cn.bcd.lib.parser.base.data.NumValGetter;
import cn.bcd.lib.parser.base.log.LogCollector_deParse;
import cn.bcd.lib.parser.base.log.LogCollector_parse;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.base.util.ParseUtil;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于字段注解生成高性能二进制解析器。
 *
 * <p>首次调用 {@link #getProcessor(Class)} 时生成并编译处理器，之后直接从缓存返回。
 * 生成的 {@link Processor} 只包含模型需要的读写逻辑，解析热路径不使用反射。</p>
 *
 * <p>配置方法必须在首次获取处理器前调用。解析和反解析日志仅用于调试，开启后会把
 * 日志代码直接加入生成的处理器并影响性能。</p>
 */
public class Parser {
    public static final Map<Class<? extends Annotation>, FieldBuilder> anno_fieldBuilder =
            Map.copyOf(ParseUtil.getAllFieldBuild());

    private static final ConcurrentHashMap<String, Processor<?>> PROCESSOR_CACHE = new ConcurrentHashMap<>();
    private static final Object PROCESSOR_BUILD_LOCK = new Object();

    private static volatile boolean configurationFrozen;
    private static volatile LogCollector_parse logCollector_parse;
    private static volatile LogCollector_deParse logCollector_deParse;
    private static volatile boolean generateClassFile;
    private static volatile boolean printBuildLog;

    /**
     * 禁用 Netty ByteBuf 的可访问性和边界检查，可提高性能，但输入错误时不再提供相同的安全保护。
     */
    public static void disableByteBufCheck() {
        System.setProperty("io.netty.buffer.checkBounds", "false");
        System.setProperty("io.netty.buffer.checkAccessible", "false");
    }

    public static void enableParseLog() {
        configure(() -> logCollector_parse = LogCollector_parse.defaultInstance);
    }

    public static void enableDeParseLog() {
        configure(() -> logCollector_deParse = LogCollector_deParse.defaultInstance);
    }

    public static void enablePrintBuildLog() {
        configure(() -> printBuildLog = true);
    }

    public static void enableGenerateClassFile() {
        configure(() -> generateClassFile = true);
    }

    private static void configure(Runnable change) {
        synchronized (PROCESSOR_BUILD_LOCK) {
            if (configurationFrozen) {
                throw new IllegalStateException("Parser configuration is frozen after the first processor lookup");
            }
            change.run();
        }
    }

    public static boolean isConfigurationFrozen() {
        return configurationFrozen;
    }

    public static LogCollector_parse parseLogCollector() {
        return logCollector_parse;
    }

    public static LogCollector_deParse deParseLogCollector() {
        return logCollector_deParse;
    }

    private static void freezeConfiguration() {
        if (!configurationFrozen) {
            synchronized (PROCESSOR_BUILD_LOCK) {
                configurationFrozen = true;
            }
        }
    }

    public static void clearProcessorCache() {
        PROCESSOR_CACHE.clear();
    }

    public static Processor<?> getCachedProcessor(String key) {
        return PROCESSOR_CACHE.get(key);
    }

    private static <T> Class<T> buildClass(Class<T> clazz, ByteOrder byteOrder, NumValGetter numValGetter) {
        ParserModelValidator.validate(clazz, numValGetter);
        return (Class<T>) ProcessorSourceBuilder.build(clazz, byteOrder, numValGetter,
                logCollector_parse != null, logCollector_deParse != null,
                printBuildLog, generateClassFile);
    }

    public static <T> Processor<T> getProcessor(Class<T> clazz) {
        return getProcessor(clazz, ByteOrder.Default, DefaultNumValGetter.instance);
    }

    /**
     * 获取指定模型、字节序和数值检查器对应的处理器。
     */
    public static <T> Processor<T> getProcessor(Class<T> clazz, ByteOrder byteOrder, NumValGetter numValGetter) {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(byteOrder, "byteOrder");
        freezeConfiguration();

        String key = ParseUtil.getProcessorKey(clazz, byteOrder, numValGetter);
        Processor<T> processor = (Processor<T>) PROCESSOR_CACHE.get(key);
        if (processor != null) {
            return processor;
        }

        synchronized (PROCESSOR_BUILD_LOCK) {
            processor = (Processor<T>) PROCESSOR_CACHE.get(key);
            if (processor == null) {
                try {
                    Class<T> processorClass = buildClass(clazz, byteOrder, numValGetter);
                    processor = (Processor<T>) processorClass.getConstructor().newInstance();
                    PROCESSOR_CACHE.put(key, processor);
                } catch (Exception exception) {
                    throw BaseException.get(exception);
                }
            }
        }
        return processor;
    }
}
