package cn.bcd.lib.spring.redis.rateControl;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.redis.RedisUtil;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式固定窗口流量控制单元。
 */
public class RedisRateControlUnit implements AutoCloseable {
    static final String REDIS_KEY_PRE_COUNT = "rc:count";
    private static final long ADD_SUCCEED = 0;

    static final DefaultRedisScript<Long> TRY_ADD_SCRIPT = new DefaultRedisScript<>(
            """
                    local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                    local increment = tonumber(ARGV[1])
                    local max = tonumber(ARGV[2])
                    local windowMillis = tonumber(ARGV[3]) * 1000
                    if current + increment > max then
                        local ttl = redis.call('PTTL', KEYS[1])
                        return -math.max(ttl, 1)
                    end
                    redis.call('INCRBY', KEYS[1], increment)
                    if current == 0 then
                        local now = redis.call('TIME')
                        local nowMillis = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
                        local ttl = windowMillis - (nowMillis % windowMillis)
                        redis.call('PEXPIRE', KEYS[1], ttl)
                    end
                    return 0
                    """,
            Long.class
    );

    private final String redisKeyCount;
    private final int timeInSecond;
    private final int maxAccessCount;
    private final RedisTemplate<String, String> redisTemplate;
    private volatile boolean available = true;

    /**
     * 创建一个流量控制单元。
     *
     * @param name                       名称，主要用于标识 Redis key
     * @param timeInSecond               固定窗口秒数
     * @param maxAccessCount             窗口内允许使用的最大额度
     * @param redisConnectionFactory     Redis 连接工厂
     */
    public RedisRateControlUnit(String name,
                                int timeInSecond,
                                int maxAccessCount,
                                RedisConnectionFactory redisConnectionFactory) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(redisConnectionFactory, "redisConnectionFactory");
        if (timeInSecond <= 0) {
            throw new IllegalArgumentException("timeInSecond must be greater than 0");
        }
        if (maxAccessCount <= 0) {
            throw new IllegalArgumentException("maxAccessCount must be greater than 0");
        }
        this.redisKeyCount = REDIS_KEY_PRE_COUNT + name;
        this.timeInSecond = timeInSecond;
        this.maxAccessCount = maxAccessCount;
        this.redisTemplate = RedisUtil.newRedisTemplate_string_string(redisConnectionFactory);
    }

    @Override
    public void close() {
        available = false;
    }

    public boolean tryAdd(int i) {
        validateIncrement(i);
        return tryAddInternal(i) == ADD_SUCCEED;
    }

    /**
     * @return 0 表示成功，负数的绝对值表示失败后需要等待的毫秒数
     */
    private long tryAddInternal(int i) {
        if (!available) {
            throw BaseException.get("rate control unit closed");
        }
        Long count = redisTemplate.execute(TRY_ADD_SCRIPT,
                Collections.singletonList(redisKeyCount),
                String.valueOf(i), String.valueOf(maxAccessCount), String.valueOf(timeInSecond));
        if (count == null) {
            throw BaseException.get("redis rate control script returned null");
        }
        return count;
    }

    public void add(int i) throws InterruptedException {
        validateIncrement(i);
        while (true) {
            long result = tryAddInternal(i);
            if (result == ADD_SUCCEED) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(-result);
        }
    }

    private void validateIncrement(int i) {
        if (i <= 0) {
            throw new IllegalArgumentException("i must be greater than 0");
        }
        if (i > maxAccessCount) {
            throw new IllegalArgumentException("i must not be greater than maxAccessCount");
        }
    }
}
