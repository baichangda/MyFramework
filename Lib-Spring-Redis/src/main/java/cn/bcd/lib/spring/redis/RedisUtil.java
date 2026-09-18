package cn.bcd.lib.spring.redis;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.redis.serializer.RedisSerializer_key_string;
import cn.bcd.lib.spring.redis.serializer.RedisSerializer_value_integer;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.JavaType;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@SuppressWarnings("unchecked")
public class RedisUtil {
    public final static String KEY_PREFIX = null;
    public final static RedisSerializer<String> SERIALIZER_KEY_STRING = KEY_PREFIX == null ?
            new StringRedisSerializer() :
            new RedisSerializer_key_string(KEY_PREFIX, StandardCharsets.UTF_8);
    public final static RedisSerializer<Object> SERIALIZER_VALUE_JDK = RedisSerializer.java();
    public final static RedisSerializer<String> SERIALIZER_VALUE_STRING = RedisSerializer.string();
    public final static RedisSerializer<Integer> SERIALIZER_VALUE_INTEGER = new RedisSerializer_value_integer();
    public final static RedisSerializer<byte[]> SERIALIZER_VALUE_BYTEARRAY = RedisSerializer.byteArray();

    /**
     * 根据值类型选择 Redis 序列化器。String 和 byte[] 使用原生序列化器，其他类型使用 Jackson。
     */
    public static <V> RedisSerializer<V> getValueSerializer(Class<V> valueClass) {
        Objects.requireNonNull(valueClass, "valueClass");
        if (valueClass == String.class) {
            return (RedisSerializer<V>) SERIALIZER_VALUE_STRING;
        } else if (valueClass == byte[].class) {
            return (RedisSerializer<V>) SERIALIZER_VALUE_BYTEARRAY;
        } else {
            return newJackson2JsonRedisSerializer(valueClass);
        }
    }

    /**
     * 获取对应实体类型的String_Jackson的redisTemplate
     *
     * @param redisConnectionFactory
     * @param type                   必须为Class或者JavaType类型
     * @param <V>
     * @return
     */
    public static <V> RedisTemplate<String, V> newRedisTemplate_string_jackson(RedisConnectionFactory redisConnectionFactory, Type type) {
        RedisTemplate<String, V> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        JacksonJsonRedisSerializer<V> redisSerializer = newJackson2JsonRedisSerializer(type);
        redisTemplate.setKeySerializer(RedisUtil.SERIALIZER_KEY_STRING);
        redisTemplate.setValueSerializer(redisSerializer);
        redisTemplate.setHashKeySerializer(RedisUtil.SERIALIZER_VALUE_STRING);
        redisTemplate.setHashValueSerializer(redisSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 获取string_bytes的redisTemplate
     *
     * @param redisConnectionFactory
     * @return
     */
    public static RedisTemplate<String, byte[]> newRedisTemplate_string_bytes(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, byte[]> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(RedisUtil.SERIALIZER_KEY_STRING);
        redisTemplate.setValueSerializer(RedisSerializer.byteArray());
        redisTemplate.setHashKeySerializer(RedisUtil.SERIALIZER_VALUE_STRING);
        redisTemplate.setHashValueSerializer(RedisSerializer.byteArray());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 获取对应的jackson序列化
     *
     * @param type
     * @param <V>
     * @return
     */
    public static <V> JacksonJsonRedisSerializer<V> newJackson2JsonRedisSerializer(Type type) {
        JacksonJsonRedisSerializer<V> redisSerializer;
        if (type instanceof Class) {
            redisSerializer = new JacksonJsonRedisSerializer<>(JsonUtil.MAPPER, (Class<V>) type);
        } else if (type instanceof JavaType) {
            redisSerializer = new JacksonJsonRedisSerializer<>(JsonUtil.MAPPER, (JavaType) type);
        } else {
            throw BaseException.get("Param Type[{0}] Not Support", type.getTypeName());
        }
        return redisSerializer;
    }

    /**
     * 获取String_String的RedisTemplate
     *
     * @param redisConnectionFactory
     * @return
     */
    public static StringRedisTemplate newRedisTemplate_string_string(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(RedisUtil.SERIALIZER_KEY_STRING);
        redisTemplate.setValueSerializer(RedisUtil.SERIALIZER_VALUE_STRING);
        redisTemplate.setHashKeySerializer(RedisUtil.SERIALIZER_VALUE_STRING);
        redisTemplate.setHashValueSerializer(RedisUtil.SERIALIZER_VALUE_STRING);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 获取String_String的RedisTemplate
     *
     * @param redisConnectionFactory
     * @return
     */
    public static RedisTemplate<String, Integer> newRedisTemplate_string_int(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Integer> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(RedisUtil.SERIALIZER_KEY_STRING);
        redisTemplate.setValueSerializer(RedisUtil.SERIALIZER_VALUE_INTEGER);
        redisTemplate.setHashKeySerializer(RedisUtil.SERIALIZER_VALUE_STRING);
        redisTemplate.setHashValueSerializer(RedisUtil.SERIALIZER_VALUE_INTEGER);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 获取String_Serializable的RedisTemplate
     *
     * @param redisConnectionFactory
     * @return
     */
    public static <V extends Serializable> RedisTemplate<String, V> newRedisTemplate_string_serializable(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, V> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(RedisUtil.SERIALIZER_KEY_STRING);
        redisTemplate.setValueSerializer(SERIALIZER_VALUE_JDK);
        redisTemplate.setHashKeySerializer(RedisUtil.SERIALIZER_VALUE_STRING);
        redisTemplate.setHashValueSerializer(SERIALIZER_VALUE_JDK);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
