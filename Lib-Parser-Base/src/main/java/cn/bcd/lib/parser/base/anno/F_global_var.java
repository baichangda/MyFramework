package cn.bcd.lib.parser.base.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将字段值存入本次顶层解析共用的全局变量。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface F_global_var {

    /**
     * 全局变量名称，不能是 {@code a-z} 单字符或纯数字字符串。
     */
    String var();
}
