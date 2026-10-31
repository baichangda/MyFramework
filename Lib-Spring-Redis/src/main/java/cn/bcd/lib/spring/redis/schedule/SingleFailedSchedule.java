package cn.bcd.lib.spring.redis.schedule;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SingleFailedSchedule {
    /**
     * 锁id
     *
     * @return
     */
    String lockId();

    /**
     * 从成功获取锁开始计算的有效期（毫秒），执行成功或失败均不重置。
     * 有效期内其他实例跳过任务；任务尚未结束但锁已过期时，其他实例可能再次获取锁。
     *
     * @return
     */
    long aliveTime() default 3000;
}
