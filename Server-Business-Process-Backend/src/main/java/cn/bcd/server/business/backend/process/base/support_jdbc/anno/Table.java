package cn.bcd.server.business.backend.process.base.support_jdbc.anno;

import cn.bcd.server.business.backend.process.base.condition.Condition;
import cn.bcd.server.business.backend.process.base.support_jdbc.bean.BaseBean;
import cn.bcd.server.business.backend.process.base.support_jdbc.bean.SuperBaseBean;
import cn.bcd.server.business.backend.process.base.support_jdbc.service.BaseService;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {
    /**
     * 表名
     */
    String value();

    /**
     * 此属性指定是否在新增时候自动设置创建信息
     * 默认开启
     * 同时满足如下条件才会自动设置
     * 1、当bean继承{@link BaseBean}
     * 2、此属性设置为true
     *
     * 影响的方法如下
     * {@link BaseService#insert(SuperBaseBean)}
     * {@link BaseService#insert(Map)}
     * {@link BaseService#insertBatch(List)}
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
     * {@link BaseService#update(SuperBaseBean)}
     * {@link BaseService#update(long, Map)}
     * {@link BaseService#update(Condition, Map)}
     * {@link BaseService#updateBatch(List)}
     */
    boolean autoSetUpdateInfo() default true;
}
