package cn.bcd.businessProcess.backend.base.support_mongodb.anno;

import cn.bcd.businessProcess.backend.base.support_mongodb.bean.BaseBean;
import cn.bcd.businessProcess.backend.base.support_mongodb.bean.SuperBaseBean;
import cn.bcd.businessProcess.backend.base.support_mongodb.service.BaseService;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DocumentExt {
    /**
     * 此属性指定是否在新增时候自动设置创建信息
     * 默认开启
     * 同时满足如下条件才会自动设置
     * 1、当bean继承{@link BaseBean}
     * 2、此属性设置为true
     *
     * 影响的方法如下
     * {@link BaseService#save(SuperBaseBean)}
     * {@link BaseService#insertAll(List)}
     */
    boolean autoSetCreateInfo() default true;

    /**
     * 此属性指定是否在更新时候自动设置更新信息
     * 默认开启
     * 同时满足如下条件才会自动设置
     * 1、当bean继承{@link BaseBean}
     * 2、此属性设置为true
     *
     * 影响的方法如下
     * {@link BaseService#save(SuperBaseBean)}
     */
    boolean autoSetUpdateInfo() default true;
}
