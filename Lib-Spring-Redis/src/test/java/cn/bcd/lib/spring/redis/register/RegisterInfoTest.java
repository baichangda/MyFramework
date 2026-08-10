package cn.bcd.lib.spring.redis.register;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

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
        RegisterInfo registerInfo = new RegisterInfo(RegisterServer.test1, new RedisTemplate<>());
        registerInfo.info = new RegisterInfo.Info(
                hosts,
                System.nanoTime() + TimeUnit.MINUTES.toNanos(1));
        return registerInfo;
    }

}
