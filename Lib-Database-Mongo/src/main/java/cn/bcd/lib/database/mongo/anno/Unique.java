package cn.bcd.lib.database.mongo.anno;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.database.mongo.bean.SuperBaseBean;
import cn.bcd.lib.database.mongo.service.BaseService;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * 唯一值验证注解
 * 在调用
 * {@link BaseService#save(SuperBaseBean)}
 * {@link BaseService#insertAll(List)}
 * 时候会进行验证
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Unique {
    /**
     * 当存在重复值数据时候返回的错误信息
     * 可以使用如下变量
     * {} 代表字段名称
     */
    String msg() default "字段[{}]值重复";

    /**
     * 错误编码
     * 在验证失败时候设置在{@link BaseException#code}中
     */
    int code() default 1;
}
