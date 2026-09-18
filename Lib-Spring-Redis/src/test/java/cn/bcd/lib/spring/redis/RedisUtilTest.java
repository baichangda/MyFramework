package cn.bcd.lib.spring.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class RedisUtilTest {

    static class Message {
        public String value;
    }

    @Test
    void getValueSerializerUsesNativeSerializersForNativeTypes() {
        assertSame(RedisUtil.SERIALIZER_VALUE_STRING, RedisUtil.getValueSerializer(String.class));
        assertSame(RedisUtil.SERIALIZER_VALUE_BYTEARRAY, RedisUtil.getValueSerializer(byte[].class));
    }

    @Test
    void getValueSerializerUsesJacksonForOtherTypes() {
        RedisSerializer<Message> serializer = RedisUtil.getValueSerializer(Message.class);

        assertInstanceOf(JacksonJsonRedisSerializer.class, serializer);
    }
}
