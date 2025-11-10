package cn.bcd.lib.parser.base.processor;

import cn.bcd.lib.parser.base.anno.F_customize;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_easy;
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
        return process(data, new ProcessContext<>(data));
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
        deProcess(data, new ProcessContext<>(data), instance);
    }

    /**
     * @param data
     * @param processContext 具体指的是当前解析返回值赋值字段所在类的解析环境、其中{@link ProcessContext#instance}代表的是所在类的实例
     *                       不能为null
     *                       1、主要用于{@link F_customize}获取父类bean
     *                       需要注意的是、如果{@link F_customize#processorClass()}中的类被多个地方复用
     *                       则需要注意每个地方的解析方法{@link #process(ByteBuf, ProcessContext)}的parentContext不一样
     *                       例如:
     *                       有如下类定义关系
     *                       class A{public B b}
     *                       class B{public C c}
     *                       class C{public D d}
     *                       class D{public int n}
     *                       那么当解析D类字段n的时候、 {@link #process(ByteBuf, ProcessContext)}的parentContext代表类D的解析环境、可以有如下取值
     *                       processContext.instance=d
     *                       processContext.processContext.instance=c
     *                       processContext.processContext.processContext.instance=b
     *                       processContext.processContext.processContext.processContext.instance=a
     *
     *                       2、用于获取父类中的全局变量
     *                       当父类中某个数字字段、使用如下注解切属性有效时
     *                       {@link F_num#globalVar()}
     *                       {@link F_bit_num#globalVar()}
     *                       {@link F_customize#globalVar()}
     *                       {@link F_bit_num_easy#globalVar()}
     *
     * @return
     */
    T process(final ByteBuf data, final ProcessContext<?> processContext);

    /**
     * @param data
     * @param processContext 和{{@link #process(ByteBuf)}}原理一致
     * @param instance
     */
    void deProcess(final ByteBuf data, final ProcessContext<?> processContext, T instance);
}
