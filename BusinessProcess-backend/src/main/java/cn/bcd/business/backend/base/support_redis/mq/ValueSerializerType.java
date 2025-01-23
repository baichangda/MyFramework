package cn.bcd.business.backend.base.support_redis.mq;

import cn.bcd.business.backend.base.support_redis.RedisUtil;

import java.lang.reflect.Type;

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