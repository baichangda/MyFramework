package cn.bcd.lib.spring.redis.rateControl;

import cn.bcd.lib.base.util.DateUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class RedisRateControlCacheTest {
    @Test
    void successfulExhaustionRejectsSubsequentConcurrentCallsLocally() {
        ScriptStub stub = new ScriptStub(60_000L);
        try (RedisRateControlUnit unit = stub.unit()) {
            assertTrue(unit.tryAdd(10));
            IntStream.range(0, 1_000).parallel().forEach(i -> assertFalse(unit.tryAdd(1)));
            assertEquals(1, stub.calls.get());
            assertThrows(IllegalArgumentException.class, () -> unit.tryAdd(0));
            unit.close();
            assertThrows(RuntimeException.class, () -> unit.tryAdd(1));
        }
    }

    @Test
    void exhaustionObservedFromAnotherInstanceAlsoCachesRejection() {
        ScriptStub stub = new ScriptStub(-120_001L);
        try (RedisRateControlUnit unit = stub.unit()) {
            assertFalse(unit.tryAdd(1));
            assertFalse(unit.tryAdd(1));
            assertEquals(1, stub.calls.get());
        }
    }

    @Test
    void insufficientQuotaForLargeIncrementDoesNotRejectSmallerIncrementLocally() {
        ScriptStub stub = new ScriptStub(-120_000L, 0L);
        try (RedisRateControlUnit unit = stub.unit()) {
            assertFalse(unit.tryAdd(3));
            assertTrue(unit.tryAdd(1));
            assertEquals(2, stub.calls.get());
        }
    }

    @Test
    void expiredCacheAccessesRedisAgain() throws Exception {
        ScriptStub stub = new ScriptStub(60_000L, 0L);
        try (RedisRateControlUnit unit = stub.unit()) {
            assertTrue(unit.tryAdd(10));
            expireCache(unit);
            assertTrue(unit.tryAdd(1));
            assertEquals(2, stub.calls.get());
        }
    }

    @Test
    void responseDelayDoesNotExtendLocalRejectionIntoNextWindow() {
        ScriptStub stub = new ScriptStub(1L, 0L);
        stub.responseDelayMillis = 10;
        try (RedisRateControlUnit unit = stub.unit()) {
            assertTrue(unit.tryAdd(10));
            assertTrue(unit.tryAdd(1));
            assertEquals(2, stub.calls.get());
        }
    }

    @Test
    void addWaitsLocallyThenRetriesRedis() throws Exception {
        ScriptStub stub = new ScriptStub(60_000L, 0L);
        try (RedisRateControlUnit unit = stub.unit()) {
            assertTrue(unit.tryAdd(10));
            Field deadline = RedisRateControlUnit.class.getDeclaredField("exhaustedUntilMillis");
            deadline.setAccessible(true);
            deadline.setLong(unit, DateUtil.CacheMillisecond.current() + 10);
            unit.add(1);
            assertEquals(2, stub.calls.get());
        }
    }

    private static void expireCache(RedisRateControlUnit unit) throws Exception {
        Field deadline = RedisRateControlUnit.class.getDeclaredField("exhaustedUntilMillis");
        deadline.setAccessible(true);
        deadline.setLong(unit, DateUtil.CacheMillisecond.current() - 1);
    }

    private static class ScriptStub {
        private final Queue<Long> responses = new ConcurrentLinkedQueue<>();
        private final AtomicInteger calls = new AtomicInteger();
        private long responseDelayMillis;

        ScriptStub(Long... responses) {
            this.responses.addAll(java.util.List.of(responses));
        }

        RedisRateControlUnit unit() {
            RedisConnection connection = (RedisConnection) Proxy.newProxyInstance(
                    RedisConnection.class.getClassLoader(), new Class<?>[]{RedisConnection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "evalSha", "eval" -> {
                            calls.incrementAndGet();
                            long responseAt = DateUtil.CacheMillisecond.current() + responseDelayMillis;
                            while (DateUtil.CacheMillisecond.current() < responseAt) {
                                TimeUnit.MILLISECONDS.sleep(1);
                            }
                            Long result = responses.poll();
                            assertNotNull(result, "Unexpected Redis request");
                            yield result;
                        }
                        case "scriptingCommands" -> proxy;
                        case "toString" -> "RateControlConnectionStub";
                        default -> method.getReturnType() == boolean.class ? false : null;
                    });
            RedisConnectionFactory factory = (RedisConnectionFactory) Proxy.newProxyInstance(
                    RedisConnectionFactory.class.getClassLoader(), new Class<?>[]{RedisConnectionFactory.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getConnection" -> connection;
                        case "toString" -> "RateControlConnectionFactoryStub";
                        default -> method.getReturnType() == boolean.class ? false : null;
                    });
            return new RedisRateControlUnit("cache-test", 60, 10, factory);
        }
    }
}
