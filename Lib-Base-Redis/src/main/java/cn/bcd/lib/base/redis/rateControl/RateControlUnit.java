package cn.bcd.lib.base.redis.rateControl;

import cn.bcd.lib.base.redis.RedisUtil;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.DateZoneUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Date;
import java.util.concurrent.*;

public class RateControlUnit {
    static Logger logger = LoggerFactory.getLogger(RateControlUnit.class);

    static final String REDIS_KEY_PRE_COUNT = "rc:count";
    static final String REDIS_KEY_PRE_RESET = "rc:reset";
    static final int REDIS_KEY_TIMEOUT_SECOND_RESET = 10;

    static final int RESET_EXECUTOR_NUM = Runtime.getRuntime().availableProcessors();
    static final ScheduledExecutorService[] RESET_EXECUTORS = new ScheduledExecutorService[RESET_EXECUTOR_NUM];

    static {
        for (int i = 0; i < RESET_EXECUTORS.length; i++) {
            int no = i + 1;
            RESET_EXECUTORS[i] = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "rateControl(" + no + "/" + RESET_EXECUTORS.length + ")");
                }
            });
        }
    }

    private final String name;
    private final String redisKeyCount;
    private final String redisKeyReset;
    private final int timeInSecond;
    private final int maxAccessCount;
    private final RedisTemplate<String, String> redisTemplate;
    private final ScheduledExecutorService resetExecutor;
    private volatile boolean reset;

    private ScheduledFuture<?> managerFuture;
    private ScheduledFuture<?> resetFuture;

    public RateControlUnit(String name,
                           int timeInSecond,
                           int maxAccessCount,
                           RedisConnectionFactory redisConnectionFactory) {
        this.name = name;
        this.redisKeyCount = REDIS_KEY_PRE_COUNT + name;
        this.redisKeyReset = REDIS_KEY_PRE_RESET + name;
        this.timeInSecond = timeInSecond;
        this.maxAccessCount = maxAccessCount;
        this.redisTemplate = RedisUtil.newRedisTemplate_string_string(redisConnectionFactory);
        this.resetExecutor = RESET_EXECUTORS[Math.floorMod(name.hashCode(), RESET_EXECUTORS.length)];
    }


    public synchronized void init() {
        int period = (REDIS_KEY_TIMEOUT_SECOND_RESET / 2) + 1;
        //尝试抢占reset
        reset = redisTemplate.opsForValue().setIfAbsent(redisKeyReset, DateZoneUtil.dateToString_second(new Date()), REDIS_KEY_TIMEOUT_SECOND_RESET, TimeUnit.SECONDS);
        if (reset) {
            logger.info("setIfAbsent reset succeed key[{}]", redisKeyReset);
            //启动周期任务清除redis计数
            startResetTask();
        }
        //启动周期任务 抢占或续期
        managerFuture = resetExecutor.scheduleAtFixedRate(() -> {
            if (reset) {
                //如果抢占成功、则此时为续期模式
                //尝试续期
                Boolean b = redisTemplate.opsForValue().setIfPresent(redisKeyReset, DateZoneUtil.dateToString_second(new Date()), REDIS_KEY_TIMEOUT_SECOND_RESET, TimeUnit.SECONDS);
                if (!b) {
                    //正常情况下不可能续期失败、如果失败、则尝试重新抢占
                    logger.warn("setIfPresent reset in executor failed key[{}]", redisKeyReset);
                    b = redisTemplate.opsForValue().setIfAbsent(redisKeyReset, DateZoneUtil.dateToString_second(new Date()), REDIS_KEY_TIMEOUT_SECOND_RESET, TimeUnit.SECONDS);
                    //如果抢占失败、说明此时被其他服务抢占、转换为抢占模式
                    if (!b) {
                        reset = false;
                        stopResetTask();
                    }
                }
            } else {
                //如果抢占失败、则此时为抢占模式
                //尝试抢占
                Boolean b = redisTemplate.opsForValue().setIfAbsent(redisKeyReset, DateZoneUtil.dateToString_second(new Date()), REDIS_KEY_TIMEOUT_SECOND_RESET, TimeUnit.SECONDS);
                if (b) {
                    //如果抢占成功、则转换为续期模式
                    reset = true;
                    logger.info("setIfAbsent reset in executor succeed key[{}]", redisKeyReset);
                    startResetTask();
                }
            }
        }, period, period, TimeUnit.SECONDS);
    }

    private synchronized void startResetTask() {
        if (resetFuture == null) {
            resetFuture = resetExecutor.scheduleAtFixedRate(() -> {
                redisTemplate.delete(redisKeyCount);
            }, timeInSecond * 1000L + DateUtil.CacheMillisecond.current() % 1000, timeInSecond * 1000L, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void stopResetTask() {
        if (resetFuture != null) {
            resetFuture.cancel(false);
            resetFuture = null;
        }
    }

    public synchronized void destroy() {
        if (managerFuture != null) {
            managerFuture.cancel(false);
        }
        stopResetTask();
    }

    public boolean access() {
        Long increment = redisTemplate.opsForValue().increment(redisKeyCount, 1);
        return increment <= maxAccessCount;
    }
}
