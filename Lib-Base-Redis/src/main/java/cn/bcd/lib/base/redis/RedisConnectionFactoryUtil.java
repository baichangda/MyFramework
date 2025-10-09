package cn.bcd.lib.base.redis;

import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

public class RedisConnectionFactoryUtil {
    public static LettuceConnectionFactory newLettuceConnectionFactory(RedisProperties redisProperties) {
        LettuceConnectionFactory connectionFactory;
        RedisProperties.Ssl ssl = redisProperties.getSsl();
        RedisProperties.Lettuce lettuce = redisProperties.getLettuce();
        RedisProperties.Pool pool = lettuce.getPool();
        RedisProperties.Cluster cluster = redisProperties.getCluster();
        if (cluster == null) {
            RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
            standaloneConfiguration.setHostName(redisProperties.getHost());
            standaloneConfiguration.setPort(redisProperties.getPort());
            standaloneConfiguration.setUsername(redisProperties.getUsername());
            standaloneConfiguration.setPassword(redisProperties.getPassword());
            standaloneConfiguration.setDatabase(redisProperties.getDatabase());
            LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = new PoolBuilderFactory().createBuilder(pool);
            if (ssl.isEnabled()) {
                connectionFactory = new LettuceConnectionFactory(standaloneConfiguration, builder.useSsl().build());
            } else {
                connectionFactory = new LettuceConnectionFactory(standaloneConfiguration, builder.build());
            }
        } else {
            RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration();
            List<RedisNode> redisNodes = cluster.getNodes().stream().map(e -> {
                String[] split = e.split(":");
                return new RedisNode(split[0], Integer.parseInt(split[1]));
            }).toList();
            clusterConfiguration.setClusterNodes(redisNodes);
            clusterConfiguration.setMaxRedirects(cluster.getMaxRedirects());
            clusterConfiguration.setUsername(redisProperties.getUsername());
            clusterConfiguration.setPassword(redisProperties.getPassword());
            LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = new PoolBuilderFactory().createBuilder(pool);
            if (ssl.isEnabled()) {
                connectionFactory = new LettuceConnectionFactory(clusterConfiguration, builder.useSsl().build());
            } else {
                connectionFactory = new LettuceConnectionFactory(clusterConfiguration, builder.build());
            }
        }
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private static final class PoolBuilderFactory {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder createBuilder(RedisProperties.Pool properties) {
            return LettucePoolingClientConfiguration.builder().poolConfig(getPoolConfig(properties));
        }

        private GenericObjectPoolConfig<StatefulConnection<?, ?>> getPoolConfig(RedisProperties.Pool properties) {
            GenericObjectPoolConfig<StatefulConnection<?, ?>> config = new GenericObjectPoolConfig<>();
            config.setMaxTotal(properties.getMaxActive());
            config.setMaxIdle(properties.getMaxIdle());
            config.setMinIdle(properties.getMinIdle());
            if (properties.getTimeBetweenEvictionRuns() != null) {
                config.setTimeBetweenEvictionRuns(properties.getTimeBetweenEvictionRuns());
            }
            if (properties.getMaxWait() != null) {
                config.setMaxWait(properties.getMaxWait());
            }
            return config;
        }
    }

    public static void main(String[] args) {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost("10.0.11.50");
        redisProperties.setPort(36379);
        redisProperties.setPassword("wq");
        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
        pool.setEnabled(true);
        pool.setMaxActive(10);
        pool.setMaxIdle(10);
        pool.setMinIdle(10);
        LettuceConnectionFactory connectionFactory = RedisConnectionFactoryUtil.newLettuceConnectionFactory(redisProperties);
        StringRedisTemplate redisTemplate = RedisUtil.newRedisTemplate_string_string(connectionFactory);
        System.out.println("-----" + redisTemplate.opsForValue().get("test"));
    }
}
