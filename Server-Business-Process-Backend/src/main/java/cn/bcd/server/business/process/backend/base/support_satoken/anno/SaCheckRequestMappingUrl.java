package cn.bcd.server.business.process.backend.base.support_satoken.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SaCheckRequestMappingUrl {
    String value() default "";
}
