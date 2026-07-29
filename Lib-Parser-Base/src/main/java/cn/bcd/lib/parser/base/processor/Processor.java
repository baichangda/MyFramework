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
     * @param processContext 一次完整编解码过程共享的上下文。字段处理器可通过
     *                       {@link ProcessContext#getParent()}获取字段所属Bean，
     *                       通过{@link ProcessContext#getParent(int)}访问更上层Bean。
     *                       生成的Bean处理器会自动维护父对象作用域；手写且继续调用
     *                       子处理器的Bean处理器必须使用
     *                       {@link ProcessContext#enter(Object)}和
     *                       {@link ProcessContext#exit()}成对维护作用域。
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
