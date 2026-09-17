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
     * 任务执行完毕key存活时间(ms)
     *
     * @return
     */
    long aliveTime() default 3000;
}
