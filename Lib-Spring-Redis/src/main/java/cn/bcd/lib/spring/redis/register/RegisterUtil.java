package cn.bcd.lib.spring.redis.register;

import cn.bcd.lib.spring.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用 Redis Sorted Set 实现的轻量级服务注册与发现。
 */
@ConditionalOnProperty(value = "register.host")
@EnableConfigurationProperties(RegisterProp.class)
@Component
public class RegisterUtil implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(RegisterUtil.class);

    public static final String redisKeyPre = "register:v2:";

    static final DefaultRedisScript<Long> HEARTBEAT_SCRIPT = new DefaultRedisScript<>(
            """
                    local time = redis.call('TIME')
                    local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
                    redis.call('ZADD', KEYS[1], now, ARGV[1])
                    redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - tonumber(ARGV[2]))
                    return now
                    """,
            Long.class
    );

    private static volatile RegisterUtil activeInstance;

    private final RegisterProp registerProp;
    private final RedisTemplate<String, String> redisTemplate;
    private final ConcurrentHashMap<RegisterServer, RegisterInfo> registerInfos = new ConcurrentHashMap<>();

    private volatile boolean running;
    private ScheduledExecutorService providerPool;
    private List<RegisterServer> providerServers = List.of();
    private String providerHost = "";

    public RegisterUtil(RegisterProp registerProp, RedisConnectionFactory redisConnectionFactory) {
        this.registerProp = Objects.requireNonNull(registerProp, "registerProp");
        this.redisTemplate = RedisUtil.newRedisTemplate_string_string(
                Objects.requireNonNull(redisConnectionFactory, "redisConnectionFactory"));
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }

        providerHost = registerProp.getHost();
        providerServers = Arrays.stream(registerProp.getServers())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        activeInstance = this;
        running = true;

        if (providerHost.isBlank() || providerServers.isEmpty()) {
            return;
        }

        AtomicInteger threadNumber = new AtomicInteger();
        providerPool = Executors.newScheduledThreadPool(providerServers.size(), runnable -> {
            Thread thread = new Thread(runnable, "redis-register-heartbeat-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        for (RegisterServer server : providerServers) {
            providerPool.scheduleAtFixedRate(
                    () -> heartbeat(server),
                    0,
                    server.provider_heartbeat_s,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        boolean terminated = stopProviderPool();
        if (terminated && !providerHost.isBlank()) {
            unregisterProvider();
        }

        registerInfos.clear();
        if (activeInstance == this) {
            activeInstance = null;
        }
        providerServers = List.of();
        providerHost = "";
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public static String host(RegisterServer server) {
        return current().registerInfo(server).host();
    }

    public static String[] hosts(RegisterServer server) {
        return current().registerInfo(server).hosts();
    }

    public static void clearCache(RegisterServer server) {
        RegisterUtil current = current();
        RegisterInfo info = current.registerInfos.get(Objects.requireNonNull(server, "server"));
        if (info != null) {
            info.clearCache();
        }
    }

    private static RegisterUtil current() {
        RegisterUtil current = activeInstance;
        if (current == null || !current.running) {
            throw new IllegalStateException("redis register is not running");
        }
        return current;
    }

    private RegisterInfo registerInfo(RegisterServer server) {
        Objects.requireNonNull(server, "server");
        return registerInfos.computeIfAbsent(server, key -> new RegisterInfo(key, redisTemplate));
    }

    private void heartbeat(RegisterServer server) {
        if (!running) {
            return;
        }
        try {
            redisTemplate.execute(
                    HEARTBEAT_SCRIPT,
                    Collections.singletonList(redisKeyPre + server.name()),
                    providerHost,
                    String.valueOf(server.consumer_providerInfoExpired_ms));
        } catch (Exception ex) {
            logger.error("redis register heartbeat failed, server[{}], host[{}]", server, providerHost, ex);
        }
    }

    private boolean stopProviderPool() {
        ScheduledExecutorService pool = providerPool;
        providerPool = null;
        if (pool == null) {
            return true;
        }
        pool.shutdown();
        try {
            if (pool.awaitTermination(5, TimeUnit.SECONDS)) {
                return true;
            }
            pool.shutdownNow();
            if (pool.awaitTermination(5, TimeUnit.SECONDS)) {
                return true;
            }
            logger.warn("redis register heartbeat pool did not terminate; provider entries will expire naturally");
            return false;
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("interrupted while stopping redis register heartbeat pool; provider entries will expire naturally");
            return false;
        }
    }

    private void unregisterProvider() {
        for (RegisterServer server : providerServers) {
            try {
                redisTemplate.opsForZSet().remove(redisKeyPre + server.name(), providerHost);
            } catch (Exception ex) {
                logger.warn("redis register unregister failed, server[{}], host[{}]", server, providerHost, ex);
            }
        }
    }
}
