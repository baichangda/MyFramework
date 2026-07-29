package cn.bcd.lib.parser.base.processor;

import io.netty.buffer.ByteBuf;

public interface Processor<T> {

    /**
     * 解析
     * 没有指定解析环境、会创建一个无父环境的解析环境
     * 对于需要依赖父环境的实例的解析方法、请使用{@link #process(ByteBuf, ProcessContext)}
     *
     * @param data
     * @return
     */
    default T process(final ByteBuf data) {
        return process(data, new ProcessContext(data));
    }

    /**
     * 反解析
     * 没有指定反解析环境、会创建一个无父环境的反解析环境
     * 对于需要依赖父环境的实例的反解析方法、请使用{@link #deProcess(ByteBuf, ProcessContext, Object)}
     *
     * @param data
     * @return
     */
    default void deProcess(final ByteBuf data, T instance) {
        deProcess(data, new ProcessContext(data), instance);
    }

    /**
     * @param data
     * @param processContext 一次完整编解码过程共享的上下文。使用
     *                       {@link cn.bcd.lib.parser.base.anno.F_cache}缓存需要跨字段处理器
     *                       访问的字段值，并通过{@link ProcessContext#getCache(int)}或
     *                       {@link ProcessContext#getCache(String)}读取。
     *
     * @return
     */
    T process(final ByteBuf data, final ProcessContext processContext);

    /**
     * @param data
     * @param processContext 和{@link #process(ByteBuf)}原理一致
     * @param instance
     */
    void deProcess(final ByteBuf data, final ProcessContext processContext, T instance);
}
