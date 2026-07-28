package cn.bcd.lib.parser.base.processor;

import io.netty.buffer.ByteBuf;

public interface Processor<T> {

    /** 为顶层解析创建共享上下文。 */
    default T process(final ByteBuf data) {
        return process(data, new ProcessContext(data));
    }

    /** 为顶层反解析创建共享上下文。 */
    default void deProcess(final ByteBuf data, T instance) {
        deProcess(data, new ProcessContext(data), instance);
    }

    /**
     * 使用顶层共享上下文解析，嵌套处理器通过全局变量传递所需值。
     *
     * @param data 数据缓冲区
     * @param processContext 共享解析上下文
     * @return 解析结果
     */
    T process(final ByteBuf data, final ProcessContext processContext);

    /**
     * 使用顶层共享上下文反解析。
     *
     * @param data 数据缓冲区
     * @param processContext 共享解析上下文
     * @param instance 待反解析对象
     */
    void deProcess(final ByteBuf data, final ProcessContext processContext, T instance);
}
