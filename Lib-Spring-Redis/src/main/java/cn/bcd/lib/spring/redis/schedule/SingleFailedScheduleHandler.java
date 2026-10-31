package cn.bcd.lib.spring.redis.schedule;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.spring.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 获取锁的实例执行任务，执行期间校验 token 并续期。
 * token 使用 yyyyMMddHHmmss 格式的当前时间，同一秒内可能重复，不能严格区分锁持有者。
 * 任务结束后停止续期，保留 key 至自然过期，避免稍晚触发的实例立即重复执行。
 * 长时间暂停或网络故障仍可能导致租约丢失，业务写入应自行保证幂等或校验 fencing token。
 */
public class SingleFailedScheduleHandler {
    private static final Logger logger = LoggerFactory.getLogger(SingleFailedScheduleHandler.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);

    private final Duration aliveTime;
    private final long aliveTimeMillis;
    private final long renewIntervalMillis;
    private final StringRedisTemplate redisTemplate;
    private final String lockId;
    /** 任务 ID 为 lockId + "," + token；相同 ID 不覆盖已有 Lease。 */
    private final ConcurrentHashMap<String, Lease> leases = new ConcurrentHashMap<>();

    public SingleFailedScheduleHandler(String lockId, RedisConnectionFactory redisConnectionFactory, Duration aliveTime) {
        this.lockId = Objects.requireNonNull(lockId, "lockId");
        if (lockId.isBlank()) {
            throw new IllegalArgumentException("lockId cannot be blank");
        }
        this.aliveTimeMillis = Objects.requireNonNull(aliveTime, "aliveTime").toMillis();
        if (aliveTimeMillis <= 0) {
            throw new IllegalArgumentException("aliveTime must be at least one millisecond");
        }
        this.aliveTime = Duration.ofMillis(aliveTimeMillis);
        renewIntervalMillis = Math.max(1, aliveTimeMillis / 3);
        redisTemplate = RedisUtil.newRedisTemplate_string_string(
                Objects.requireNonNull(redisConnectionFactory, "redisConnectionFactory"));
    }

    public SingleFailedScheduleHandler(SingleFailedSchedule anno, RedisConnectionFactory redisConnectionFactory) {
        this(anno.lockId(), redisConnectionFactory, Duration.ofMillis(anno.aliveTime()));
    }

    /**
     * 成功获取锁并启动续期时返回本次任务 ID；未获取到锁或 Redis 请求失败时返回 null。
     * 调用方必须在 finally 中将返回的 ID 传给 doAfterFinish(id)，允许跨线程结束。
     */
    public String doBeforeStart() {
        String token = DateUtil.FORMATTER_yyyyMMddHHmmss.format(Instant.now());
        String taskId = lockId + "," + token;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockId, token, aliveTime);
            if (!Boolean.TRUE.equals(acquired)) {
                return null;
            }
            Lease lease = new Lease(token, taskId);
            if (leases.putIfAbsent(taskId, lease) != null) {
                lease.close();
                return null;
            }
            return taskId;
        } catch (Exception ex) {
            logger.error("schedule lock acquisition failed taskId[{}]", taskId, ex);
            return null;
        }
    }

    /**
     * 按任务 ID 移除本次执行并停止续期；null、未知 ID 或重复调用时不做任何操作。
     */
    public void doAfterFinish(String taskId) {
        if (taskId == null) {
            return;
        }
        Lease lease = leases.remove(taskId);
        if (lease != null) {
            lease.close();
        }
    }

    /**
     * 本次执行独占的续期状态；关闭只停止续期，不删除或重置 Redis key。
     */
    private final class Lease {
        private final String token;
        private final String taskId;
        private final Thread renewalThread;
        private volatile boolean closed;

        private Lease(String token, String taskId) {
            this.token = token;
            this.taskId = taskId;
            renewalThread = Thread.ofVirtual().name("redis-schedule-renew-" + lockId).unstarted(this::renew);
            renewalThread.start();
        }

        private void renew() {
            try {
                while (!closed) {
                    Thread.sleep(renewIntervalMillis);
                    if (closed) {
                        return;
                    }
                    Long result = redisTemplate.execute(RENEW_SCRIPT, List.of(lockId),
                            token, Long.toString(aliveTimeMillis));
                    if (!Long.valueOf(1).equals(result)) {
                        logger.warn("schedule lease lost, stop renewing taskId[{}]", taskId);
                        return;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                if (!closed) {
                    logger.error("schedule lease renewal failed, stop renewing taskId[{}]", taskId, ex);
                }
            }
        }

        private void close() {
            closed = true;
            renewalThread.interrupt();
        }
    }
}
