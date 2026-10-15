package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.data.StringAppendMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于原始数据是字节数组代表字符串的情况
 *
 * 用于如下类型字段
 * String
 * 反解析中
 * 值可以为null、此时按空字符串处理；补齐模式写入指定长度的零字节，noAppend模式仍检查编码长度
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface F_string {
    /**
     * 是否跳过当前字段。为 {@code true} 时，解析阶段按字符串编码长度跳过，
     * 反解析阶段写入相同长度、值为 {@code 0} 的字节。
     */
    boolean skip() default false;

    /**
     * 占用字节数
     * 1-8
     * 与{@link #lenExpr()}互斥
     */
    int len() default 0;

    /**
     * 字段所占字节长度表达式
     * 用于固定长度字段解析,配合var参数使用,代表的是Byte的长度
     * 与{@link #len()}互斥
     * 变量取值来源于var、globalVar
     * 例如:
     * m
     * m*n
     * a*b-1
     * a*(b-2)
     * a*(b-2)+A
     */
    String lenExpr() default "";

    /**
     * 字符串编码，面向UTF-8、GBK、ASCII等编码，不考虑UTF-16、UTF-32
     */
    String charset() default "UTF-8";

    /**
     * 在写入时候
     * 按编码后的字节数计算长度，小于指定长度时根据策略补零，超长时抛出异常
     * 解析时先解码，再按对应方向移除首尾的NUL字符；noAppend不移除
     * 填充模式如下
     * {@link StringAppendMode#noAppend} 不补
     * {@link StringAppendMode#lowAddressAppend} 低内存地址补
     * {@link StringAppendMode#highAddressAppend} 高内存地址补
     * <p>
     * 内存地址解释
     * 假如有byte[4]、其中0是低内存地址、3是高内存地址
     */
    StringAppendMode appendMode() default StringAppendMode.noAppend;

}
