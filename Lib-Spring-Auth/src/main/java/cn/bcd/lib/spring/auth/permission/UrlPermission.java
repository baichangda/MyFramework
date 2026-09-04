package cn.bcd.lib.spring.auth.permission;

import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 必须用于{@link RequestMapping}注解标注的方法
 * 用于收集微服务中的权限数据
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlPermission {
    /**
     * 权限值
     */
    String value();

    /**
     * 权限描述
     */
    String remark() default "";
}
