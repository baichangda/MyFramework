package cn.bcd.app.bp.backend.base.support_spring_cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.core.types.Expiration;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisCacheConfigTest {
    @Test
    void concurrentMissesAcrossCacheInstancesOnlyLoadOnce() throws Exception {
        CountDownLatch secondRead = new CountDownLatch(1);
        RedisConnectionFactory factory = inMemoryRedis(secondRead);
        Cache firstCache = new RedisCacheConfig().redisCache(factory);
        Cache secondCache = new RedisCacheConfig().redisCache(factory);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Callable<String> loader = () -> {
            calls.incrementAndGet();
            loading.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("loader timed out");
            }
            return "loaded";
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> firstCache.get("key", loader));
            assertTrue(loading.await(5, TimeUnit.SECONDS));
            Future<String> second = executor.submit(() -> secondCache.get("key", loader));
            try {
                assertTrue(secondRead.await(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            assertEquals("loaded", first.get(5, TimeUnit.SECONDS));
            assertEquals("loaded", second.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void loaderFailureReleasesLockAndDoesNotCacheFailure() {
        Cache cache = new RedisCacheConfig().redisCache(inMemoryRedis(new CountDownLatch(1)));
        assertThrows(Cache.ValueRetrievalException.class,
                () -> cache.get("key", () -> { throw new IllegalArgumentException("failure"); }));
        assertEquals("recovered", cache.get("key", () -> "recovered"));
    }

    private RedisConnectionFactory inMemoryRedis(CountDownLatch lockObserved) {
        Map<String, byte[]> values = new ConcurrentHashMap<>();
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        RedisKeyCommands keys = mock(RedisKeyCommands.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.stringCommands()).thenReturn(strings);
        when(connection.keyCommands()).thenReturn(keys);
        when(strings.get(any(byte[].class))).thenAnswer(invocation -> values.get(key(invocation.getArgument(0))));
        when(strings.set(any(byte[].class), any(byte[].class), any(SetCondition.class), any(Expiration.class)))
                .thenAnswer(invocation -> {
                    String key = key(invocation.getArgument(0));
                    byte[] value = invocation.getArgument(1);
                    SetCondition condition = invocation.getArgument(2);
                    if (condition == SetCondition.ifAbsent()) {
                        return values.putIfAbsent(key, value) == null;
                    }
                    values.put(key, value);
                    return true;
                });
        when(keys.exists(any(byte[].class))).thenAnswer(invocation -> {
            boolean exists = values.containsKey(key(invocation.getArgument(0)));
            if (exists) {
                lockObserved.countDown();
            }
            return exists;
        });
        when(keys.del(any(byte[][].class))).thenAnswer(invocation -> {
            long count = 0;
            for (byte[] key : (byte[][]) invocation.getRawArguments()[0]) {
                if (values.remove(key(key)) != null) {
                    count++;
                }
            }
            return count;
        });
        return factory;
    }

    private static String key(byte[] key) {
        return new String(key, StandardCharsets.UTF_8);
    }
}
