package cn.bcd.lib.spring.redis.rateControl;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisRateControlUnitTest {

    @Test
    void rejectsInvalidConfiguration() {
        RedisConnectionFactory connectionFactory = connectionFactoryStub();

        assertThrows(NullPointerException.class, () -> new RedisRateControlUnit(null, 1, 1, connectionFactory));
        assertThrows(NullPointerException.class, () -> new RedisRateControlUnit("test", 1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new RedisRateControlUnit("test", 0, 1, connectionFactory));
        assertThrows(IllegalArgumentException.class, () -> new RedisRateControlUnit("test", 1, 0, connectionFactory));
    }

    @Test
    void rejectsInvalidIncrementWithoutAccessingRedis() {
        try (RedisRateControlUnit unit = new RedisRateControlUnit("test", 1, 1, connectionFactoryStub())) {
            assertThrows(IllegalArgumentException.class, () -> unit.tryAdd(0));
            assertThrows(IllegalArgumentException.class, () -> unit.tryAdd(-1));
            assertThrows(IllegalArgumentException.class, () -> unit.tryAdd(2));
            assertThrows(IllegalArgumentException.class, () -> unit.add(2));
        }
    }

    @Test
    void usesWindowTtlAsRetryDelay() {
        assertEquals(5_000, RedisRateControlUnit.retryDelayMillis(-5_001));
        assertEquals(1, RedisRateControlUnit.retryDelayMillis(-1));
    }

    @Test
    void closeIsIdempotent() {
        RedisRateControlUnit unit = new RedisRateControlUnit("test", 1, 1, connectionFactoryStub());

        assertDoesNotThrow(unit::close);
        assertDoesNotThrow(unit::close);
        assertThrows(RuntimeException.class, () -> unit.tryAdd(1));
    }

    private static RedisConnectionFactory connectionFactoryStub() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) {
                        return "RedisConnectionFactoryStub";
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    return null;
                });
    }
}
