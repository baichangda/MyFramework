package cn.bcd.lib.spring.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.io.Serializable;
import java.util.concurrent.Executors;

@Configuration
public class RedisConfig {
    /**
     * key 用 {@link RedisUtil#SERIALIZER_KEY_STRING}
     * value 用 {@link RedisUtil#SERIALIZER_VALUE_JDK}
     * 的 RedisTemplate
     *
     * @return
     */
    @Bean(name = "string_serializable_redisTemplate")
    public RedisTemplate<String, Serializable> string_serializable_redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return RedisUtil.newRedisTemplate_string_serializable(redisConnectionFactory);
    }

    /**
     * key 用 {@link RedisUtil#SERIALIZER_KEY_STRING}
     * value 用 {@link RedisUtil#SERIALIZER_VALUE_STRING}
     * 的 RedisTemplate
     *
     * @return
     */
    @Bean(name = "string_string_redisTemplate")
    public StringRedisTemplate string_string_redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return RedisUtil.newRedisTemplate_string_string(redisConnectionFactory);
    }

    @Bean
    @Lazy
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
        redisMessageListenerContainer.setConnectionFactory(factory);
        redisMessageListenerContainer.setTaskExecutor(Executors.newSingleThreadExecutor());
        return redisMessageListenerContainer;
    }

}