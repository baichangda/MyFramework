package cn.bcd.lib.parser.base.processor;

import io.netty.buffer.ByteBuf;

/** 二进制解析和反解析处理器。 */
public interface Processor<T> {

    /**
     * 创建一次顶层解析上下文并解析对象。
     *
     * @param data 输入数据
     * @return 解析结果
     */
    default T process(final ByteBuf data) {
        return process(data, new ProcessContext(data));
    }

    /**
     * 创建一次顶层反解析上下文并写出对象。
     *
     * @param data 输出数据
     * @param instance 待写出的对象
     */
    default void deProcess(final ByteBuf data, T instance) {
        deProcess(data, new ProcessContext(data), instance);
    }

    /**
     * 使用一次解析共享的ProcessContext解析对象。
     *
     * <p>生成的处理器会自动维护{@link ProcessContext#root}和{@link ProcessContext#parent}。
     * root是本次解析的顶层对象，parent是当前字段所属对象。子对象必须复用传入的context，
     * 不得再创建子ProcessContext。</p>
     *
     * <p>手写处理器如果创建对象后还会调用子处理器，必须使用
     * {@link ProcessContext#enter(Object)}进入对象作用域，并在finally中调用
     * {@link ProcessContext#exit(Object)}恢复parent。</p>
     *
     * @param data 输入数据
     * @param processContext 本次完整解析共享的上下文，不能为null
     * @return 解析结果
     */
    T process(final ByteBuf data, final ProcessContext processContext);

    /**
     * 使用一次反解析共享的ProcessContext写出对象，生命周期规则与
     * {@link #process(ByteBuf, ProcessContext)}一致。
     *
     * @param data 输出数据
     * @param processContext 本次完整反解析共享的上下文，不能为null
     * @param instance 待写出的对象
     */
    void deProcess(final ByteBuf data, final ProcessContext processContext, T instance);
}
