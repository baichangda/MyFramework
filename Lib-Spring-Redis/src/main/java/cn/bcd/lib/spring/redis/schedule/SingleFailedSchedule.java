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
     * 锁的租期（毫秒，必须大于 0），同步任务执行期间每隔约三分之一租期校验归属并续期。
     * 任务结束后停止续期，key 按最后一次获取或续期设置的有效期自然过期。
     * 长时间暂停或网络故障仍可能导致租约丢失，不提供严格的恰好一次执行保证。
     *
     * @return
     */
    long aliveTime() default 3000;
}
