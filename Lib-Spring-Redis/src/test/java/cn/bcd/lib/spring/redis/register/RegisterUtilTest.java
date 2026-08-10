package cn.bcd.lib.spring.redis.register;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterUtilTest {

    @Test
    void consumerOnlyLifecycleIsIdempotent() {
        RegisterProp prop = new RegisterProp();
        RegisterUtil registerUtil = new RegisterUtil(prop, connectionFactoryStub());

        assertDoesNotThrow(registerUtil::start);
        assertDoesNotThrow(registerUtil::start);
        assertTrue(registerUtil.isRunning());

        assertDoesNotThrow(() -> registerUtil.stop());
        assertDoesNotThrow(() -> registerUtil.stop());
        assertFalse(registerUtil.isRunning());
        assertThrows(IllegalStateException.class, () -> RegisterUtil.host(RegisterServer.test1));
    }

    @Test
    void propertiesNormalizeNullValues() {
        RegisterProp prop = new RegisterProp();

        prop.setHost(null);
        prop.setServers(null);

        assertTrue(prop.getHost().isEmpty());
        assertEquals(0, prop.getServers().length);

        prop.host = null;
        prop.servers = null;

        assertTrue(prop.getHost().isEmpty());
        assertEquals(0, prop.getServers().length);
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
