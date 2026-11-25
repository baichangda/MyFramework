package cn.bcd.lib.spring.auth.permission;

import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 必须用于{@link RequestMapping}注解标注的方法
 * 声明接口权限的名称，资源取自接口路径。
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlPermission {
    String value();
}
