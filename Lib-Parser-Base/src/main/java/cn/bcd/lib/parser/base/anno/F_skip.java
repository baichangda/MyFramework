package cn.bcd.lib.parser.base.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 在字段解析前后跳过数个字节
 *
 * 用于和其他字段一起配合使用、不能单独使用
 * 适用于任何字段
 *
 * 注意:
 * 如果和{@link F_bit_num}、{@link F_bit_num_array}一起配合使用、需要保证skip之前的bit解析不存在多余的bit
 * 即{@link #lenBefore()}、{@link #lenExprBefore()}要保证上一个字段解析结束时、没有多余bit
 * 即{@link #lenAfter()}、{@link #lenExprAfter()}要保证本字段解析结束时、没有多余bit
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface F_skip {
    /**
     * 解析前跳过字节
     * 和{@link #lenExprBefore()}互斥
     */
    int lenBefore() default 0;

    /**
     * 解析前跳过字节表达式
     * 和{@link #lenBefore()}互斥
     * 变量取值来源于numVar、globalNumVar
     * 例如:
     * m
     * m*n
     * a*b-1
     * a*(b-2)
     * a*(b-2)+A
     */
    String lenExprBefore() default "";


    /**
     * 解析后跳过字节
     * 和{@link #lenExprAfter()}互斥
     */
    int lenAfter() default 0;

    /**
     * 解析前跳过字节表达式
     * 和{@link #lenAfter()}互斥
     * 变量取值来源于numVar、globalNumVar
     * 例如:
     * m
     * m*n
     * a*b-1
     * a*(b-2)
     * a*(b-2)+A
     */
    String lenExprAfter() default "";
}
