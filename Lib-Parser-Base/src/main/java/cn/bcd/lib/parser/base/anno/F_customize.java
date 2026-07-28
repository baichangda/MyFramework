package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 适用于任何字段
 * 用户自己实现解析逻辑
 * <p>
 * 反解析中
 * 值可以为null、null的含义由定制逻辑自己实现
 *
 * 开启解析或反解析日志后、会统一记录此字段消费或写入的字节和字段值
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)

public @interface F_customize {
    /**
     * 处理类
     * 必须是{@link Processor}子类
     */
    Class<?> processorClass();

    /**
     * 处理类参数
     * 在new {@link #processorClass()}时候、会传入指定参数、以,分割
     * 空字符串、则不传入参数
     * 如果传入参数、要求指定处理类必须有此构造方法
     * 参数类型支持java类型有、int、long、float、double、String
     * 例如有5个参数
     * int、long、float、double、String
     * 则值可以是
     * "100,1000L,1.123F,100.123,\"test\""
     */
    String processorArgs() default "";

    /**
     * 当前类内数值变量，取值范围为 {@code a-z}，{@code 0} 表示不定义。
     */
    char var() default '0';

}
