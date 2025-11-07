package cn.bcd.lib.spring.redis.mq;

import java.lang.reflect.Type;
import cn.bcd.lib.spring.redis.RedisUtil;

public enum ValueSerializerType {
    /**
     * {@link RedisUtil#SERIALIZER_VALUE_BYTEARRAY}
     */
    BYTE_ARRAY,
    /**
     * {@link RedisUtil#SERIALIZER_VALUE_STRING}
     */
    STRING,
    /**
     * {@link RedisUtil#newJackson2JsonRedisSerializer(Type)}
     */
    JACKSON,
    /**
     * {@link RedisUtil#SERIALIZER_VALUE_JDK}
     */
    SERIALIZABLE
}