package cn.bcd.lib.spring.monitor.client;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MonitorRedisTopicMQTest {
    @Test
    void reportsIdentityBatchAndExtensionUsingSharedWireFormat() {
        MonitorProp prop = properties();
        CapturingRedis redis = new CapturingRedis();
        try (MonitorRedisTopicMQ client = client(prop, redis)) {
            client.monitorExtCollector = () -> Map.of("count", 7);
            client.onMessage("20261020120000");
            assertEquals("monitorResponse:20261020120000", redis.key);
            @SuppressWarnings("unchecked")
            org.springframework.data.redis.serializer.RedisSerializer<MonitorData> serializer =
                    (org.springframework.data.redis.serializer.RedisSerializer<MonitorData>) redis.getValueSerializer();
            MonitorData decoded = serializer.deserialize(serializer.serialize(redis.data));
            assertEquals("backend-01", decoded.serverId);
            assertEquals(3, decoded.serverType);
            assertEquals(20261020120000L, decoded.batch);
            assertNotNull(decoded.systemData);
            assertEquals(7, decoded.extData.get("count"));
            assertTrue(redis.script.contains("EXPIRE"));
            assertTrue(redis.script.contains("LPUSH"));
        }
    }

    @Test
    void extensionFailureStillReportsSystemData() {
        CapturingRedis redis = new CapturingRedis();
        try (MonitorRedisTopicMQ client = client(properties(), redis)) {
            client.monitorExtCollector = () -> { throw new IllegalStateException("failed"); };
            client.onMessage("20261020120000");
            assertNotNull(redis.data.systemData);
            assertNull(redis.data.extData);
        }
    }

    @Test
    void extensionRunsSynchronouslyBeforePublishing() {
        CapturingRedis redis = new CapturingRedis();
        Thread caller = Thread.currentThread();
        try (MonitorRedisTopicMQ client = client(properties(), redis)) {
            client.monitorExtCollector = () -> {
                assertSame(caller, Thread.currentThread());
                assertNull(redis.data);
                return Map.of("collected", true);
            };
            client.onMessage("20261020120000");
            assertEquals(Boolean.TRUE, redis.data.extData.get("collected"));
        }
    }

    @Test
    void initializationWaitsForDependencyInjectionAndInvalidRequestsAreRejected() {
        CapturingRedis redis = new CapturingRedis();
        try (MonitorRedisTopicMQ client = client(properties(), redis)) {
            assertFalse(((TestClient) client).initialized);
            client.afterPropertiesSet();
            assertTrue(((TestClient) client).initialized);
            assertThrows(NumberFormatException.class, () -> client.onMessage("bad"));
            assertThrows(IllegalArgumentException.class, () -> client.onMessage("0"));
            assertNull(redis.data);
        }
    }

    static MonitorProp properties() {
        MonitorProp prop = new MonitorProp();
        prop.serverId = "backend-01";
        prop.serverType = 3;
        return prop;
    }

    static RedisConnectionFactory factory() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class}, (proxy, method, args) ->
                        method.getReturnType() == boolean.class ? false : null);
    }

    static TestClient client(MonitorProp prop, CapturingRedis redis) {
        TestClient client = new TestClient(prop);
        redis.setValueSerializer(client.redisTemplate.getValueSerializer());
        client.redisTemplate = redis;
        return client;
    }

    static class TestClient extends MonitorRedisTopicMQ {
        boolean initialized;
        TestClient(MonitorProp prop) {
            super(factory(), prop);
        }
        @Override
        public void init() {
            initialized = true;
        }
        @Override
        protected SystemData collectSystemData() {
            return new SystemData();
        }
    }

    static class CapturingRedis extends RedisTemplate<String, MonitorData> {
        String key;
        MonitorData data;
        String script;
        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            this.key = keys.getFirst();
            this.data = (MonitorData) args[0];
            this.script = script.getScriptAsString();
            return (T) Long.valueOf(1);
        }
    }
}
