package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.data.BitRemainingMode;
import cn.bcd.lib.parser.base.processor.ProcessContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于原始数据是bit位代表的数字
 *
 * 适用如下字段类型
 * byte、short、int、long、float、double、枚举类
 * <p>
 * 枚举类
 * 仅支持整型数字
 * 要求枚举类必有如下静态方法、例如
 * public enum Example{
 *     public static Example fromInteger(int i){}
 *     public int toInteger(){}
 * }
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface F_bit_num {
    /**
     * 是否跳过当前字段。为 {@code true} 时，解析阶段跳过该字段占用的位，
     * 反解析阶段写入相同位数的 {@code 0}。
     */
    boolean skip() default false;

    /**
     * 占用bit位
     */
    int len();


    /**
     * 表示当前字段bit解析结束时候、剩余多余的bit(不满1字节的)的处理模式
     */
    BitRemainingMode bitRemainingMode() default BitRemainingMode.Default;

    /**
     * 值处理表达式
     * 在解析出的原始值得基础上,进行运算
     * 公式中的x变量代表字段原始的值
     * 注意:
     * 表达式需要符合java运算表达式规则
     * 最好先进行加减运算、再进行乘除运算、这样可以避免精度问题、例如 (11-10)/10 和 11/10-1 的结果不同
     * 例如:
     * x-10
     * x*10
     * (x+10)*100
     * (x+100)/100
     */
    String valExpr() default "";

    /**
     * 当前类内数值变量，取值范围为 {@code a-z}，{@code 0} 表示不定义。
     */
    char var() default '0';

    /**
     * bit位表示的值是否为无符号类型
     * 当是有符号类型时候
     * bit最高位为符号位、0代表正数、1代表负数
     * 对值的求解方式为
     * 正数、正常进行求值
     * 负数、所有bit位取反+1、求值后、代表负数
     */
    boolean unsigned() default true;


    /**
     * 在解析之前跳过多少bit
     */
    int skipBefore() default 0;


    /**
     * 在解析之后跳过多少bit
     */
    int skipAfter() default 0;

    /**
     * 结果小数精度、会四舍五入
     * 默认-1、代表不进行精度处理、最大为10
     * 仅当字段类型为float、double时候、此属性才有效
     */
    int precision() default -1;
}
