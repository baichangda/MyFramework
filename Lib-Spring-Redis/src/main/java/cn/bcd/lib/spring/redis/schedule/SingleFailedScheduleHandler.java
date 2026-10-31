package cn.bcd.lib.spring.redis.schedule;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

/**
 * 固定时间窗口内由获取到锁的实例执行任务，结果取决于该实例的执行结果。
 * <p>
 * 原理:
 * 1、到达任务时间点,集群多个实例开始争夺redis锁
 * 2、成功获取锁时设置有效期 aliveTime，随后执行任务；成功或失败均不重置有效期。
 * 3、锁有效期内其他实例跳过任务；锁过期不代表原任务已完成，本实现不自动续期。
 */
public class SingleFailedScheduleHandler {

    static Logger logger = LoggerFactory.getLogger(SingleFailedScheduleHandler.class);

    /**
     * 从成功获取锁开始计算的有效期，任务执行结果不改变该有效期。
     */
    private final Duration aliveTime;
    protected ValueOperations<String, String> valueOperations;
    /**
     * 定时任务的锁表示字符串,确保每一个定时任务设置不同的锁id
     */
    protected String lockId;

    public SingleFailedScheduleHandler(String lockId, RedisConnectionFactory redisConnectionFactory, Duration aliveTime) {
        this.lockId = lockId;
        this.valueOperations = RedisUtil.newRedisTemplate_string_string(redisConnectionFactory).opsForValue();
        this.aliveTime = aliveTime;
    }

    public SingleFailedScheduleHandler(SingleFailedSchedule anno, RedisConnectionFactory redisConnectionFactory) {
        this(anno.lockId(), redisConnectionFactory, Duration.ofMillis(anno.aliveTime()));
    }

    /**
     * 执行任务之前调用
     *
     * @return
     */
    public boolean doBeforeStart() {
        //1、获取锁
        Boolean res = valueOperations.setIfAbsent(lockId, "0", aliveTime);
        if (res == null) {
            throw BaseException.get("doBeforeStart lockId[{}] res null", lockId);
        }
        return res;
    }

    /**
     * 执行失败时调用
     * 什么事情都不做
     */
    public void doOnFailed() {
    }

    /**
     * 执行成功时调用
     * 什么事情都不做
     */
    public void doOnSuccess() {
    }
}
