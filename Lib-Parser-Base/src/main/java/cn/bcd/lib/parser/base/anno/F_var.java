package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.processor.ProcessContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将字段值存入本次解析共用的 {@link ProcessContext#vars}。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface F_var {
    int index();
}
