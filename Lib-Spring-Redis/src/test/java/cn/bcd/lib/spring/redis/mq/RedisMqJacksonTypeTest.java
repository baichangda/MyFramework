package cn.bcd.lib.spring.redis.mq;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RedisMqJacksonTypeTest {

    record Message(String value) {
    }

    @Test
    void classConstructorsCreateJacksonSerializer() {
        RedisConnectionFactory connectionFactory = connectionFactoryStub();

        assertDoesNotThrow(() -> new RedisQueueMQ<Message>(
                "queue", connectionFactory, Message.class, 1, 1));
        assertDoesNotThrow(() -> new RedisTopicMQ<Message>(
                connectionFactory, 1, 1, Message.class, "topic"));
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
