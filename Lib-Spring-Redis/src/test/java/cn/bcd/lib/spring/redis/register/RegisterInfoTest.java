package cn.bcd.lib.spring.redis.register;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RegisterInfoTest {

    @Test
    void doesNotExposeCachedHostArray() {
        RegisterInfo registerInfo = registerInfoWithHosts("a", "b");

        String[] hosts = registerInfo.hosts();
        hosts[0] = "changed";

        assertArrayEquals(new String[]{"a", "b"}, registerInfo.hosts());
    }

    @Test
    void roundRobinWorksAcrossIndexOverflow() {
        RegisterInfo registerInfo = registerInfoWithHosts("a", "b");
        registerInfo.index.set(Long.MAX_VALUE);

        assertEquals("b", registerInfo.host());
        assertEquals("a", registerInfo.host());
    }

    @Test
    void timeoutPartsAddUpToMaximumTimeout() {
        for (RegisterServer server : RegisterServer.values()) {
            assertEquals(
                    server.maxTimeout_s * 1000L,
                    server.consumer_localCacheExpired_ms + server.consumer_providerInfoExpired_ms);
        }
    }

    private static RegisterInfo registerInfoWithHosts(String... hosts) {
        RegisterInfo registerInfo = new RegisterInfo(RegisterServer.test1, connectionFactoryStub());
        registerInfo.info = new RegisterInfo.Info(
                hosts,
                System.nanoTime() + TimeUnit.MINUTES.toNanos(1));
        return registerInfo;
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
