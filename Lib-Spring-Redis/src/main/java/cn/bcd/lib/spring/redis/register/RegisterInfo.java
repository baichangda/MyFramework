package cn.bcd.lib.spring.redis.register;

import cn.bcd.lib.base.exception.BaseException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class RegisterInfo {
    private static final DefaultRedisScript<List> DISCOVER_SCRIPT = new DefaultRedisScript<>(
            """
                    local time = redis.call('TIME')
                    local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
                    local expiredBefore = now - tonumber(ARGV[1])
                    redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', expiredBefore)
                    return redis.call('ZRANGE', KEYS[1], 0, -1)
                    """,
            List.class
    );

    private final RegisterServer server;
    private final RedisTemplate<String, String> redisTemplate;
    private final String redisKey;

    record Info(String[] hosts, long expireAtNanos) {
    }

    volatile Info info;
    final AtomicLong index = new AtomicLong();

    RegisterInfo(RegisterServer server, RedisTemplate<String, String> redisTemplate) {
        this.server = Objects.requireNonNull(server, "server");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.redisKey = RegisterUtil.redisKeyPre + server.name();
    }

    String[] hosts() {
        return cachedHosts().clone();
    }

    String host() {
        String[] hosts = cachedHosts();
        if (hosts.length == 0) {
            return null;
        }
        return hosts[Math.floorMod(index.getAndIncrement(), hosts.length)];
    }

    void clearCache() {
        info = null;
    }

    private String[] cachedHosts() {
        long now = System.nanoTime();
        Info current = info;
        if (current == null || now - current.expireAtNanos >= 0) {
            synchronized (this) {
                now = System.nanoTime();
                current = info;
                if (current == null || now - current.expireAtNanos >= 0) {
                    String[] hosts = loadHosts();
                    long cacheNanos = TimeUnit.MILLISECONDS.toNanos(server.consumer_localCacheExpired_ms);
                    info = new Info(hosts, now + cacheNanos);
                    return hosts;
                }
            }
        }
        return current.hosts;
    }

    private String[] loadHosts() {
        List<?> result = redisTemplate.execute(
                DISCOVER_SCRIPT,
                Collections.singletonList(redisKey),
                String.valueOf(server.consumer_providerInfoExpired_ms));
        if (result == null) {
            throw BaseException.get("redis register discover script returned null, server[{}]", server.name());
        }
        String[] hosts = result.stream().map(String::valueOf).toArray(String[]::new);
        Arrays.sort(hosts);
        return hosts;
    }
}
