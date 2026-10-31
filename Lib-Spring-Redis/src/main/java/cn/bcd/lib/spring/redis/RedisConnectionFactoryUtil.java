package cn.bcd.lib.spring.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class RedisConnectionFactoryUtil {
    /** 创建并初始化连接工厂；未交给 Spring 容器管理时，调用方负责在结束后调用 destroy()。 */
    public static LettuceConnectionFactory newLettuceConnectionFactory(DataRedisProperties redisProperties) {
        Objects.requireNonNull(redisProperties, "redisProperties");
        URI url = parseUrl(redisProperties.getUrl());
        RedisStandaloneConfiguration standalone = standaloneConfiguration(redisProperties, url);
        boolean clusterMode = redisProperties.getSentinel() == null && redisProperties.getCluster() != null;
        LettuceClientConfiguration client = clientConfiguration(redisProperties, url, clusterMode);
        LettuceConnectionFactory factory;
        if (redisProperties.getSentinel() != null) {
            DataRedisProperties.Sentinel properties = redisProperties.getSentinel();
            Assert.hasText(properties.getMaster(), "Sentinel master must not be blank");
            Assert.notEmpty(properties.getNodes(), "Sentinel nodes must not be empty");
            RedisSentinelConfiguration sentinel = new RedisSentinelConfiguration();
            sentinel.master(properties.getMaster());
            sentinel.setSentinels(properties.getNodes().stream()
                    .map(node -> RedisNode.fromString(node, RedisNode.DEFAULT_SENTINEL_PORT)).toList());
            sentinel.setUsername(standalone.getUsername());
            sentinel.setPassword(standalone.getPassword());
            sentinel.setDatabase(standalone.getDatabase());
            sentinel.setSentinelUsername(properties.getUsername());
            sentinel.setSentinelPassword(RedisPassword.of(properties.getPassword()));
            factory = new LettuceConnectionFactory(sentinel, client);
        } else if (clusterMode) {
            DataRedisProperties.Cluster properties = redisProperties.getCluster();
            Assert.notEmpty(properties.getNodes(), "Cluster nodes must not be empty");
            RedisClusterConfiguration cluster = new RedisClusterConfiguration();
            cluster.setClusterNodes(properties.getNodes().stream().map(RedisNode::fromString).toList());
            if (properties.getMaxRedirects() != null) {
                cluster.setMaxRedirects(properties.getMaxRedirects());
            }
            cluster.setUsername(standalone.getUsername());
            cluster.setPassword(standalone.getPassword());
            factory = new LettuceConnectionFactory(cluster, client);
        } else {
            factory = new LettuceConnectionFactory(standalone, client);
        }
        return initialize(factory);
    }

    private static URI parseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        URI url = URI.create(value);
        Assert.isTrue("redis".equals(url.getScheme()) || "rediss".equals(url.getScheme()),
                "Redis URL must use redis or rediss");
        Assert.hasText(url.getHost(), "Redis URL must contain a host");
        return url;
    }

    private static RedisStandaloneConfiguration standaloneConfiguration(DataRedisProperties properties, URI url) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        if (url == null) {
            config.setHostName(properties.getHost());
            config.setPort(properties.getPort());
            config.setUsername(properties.getUsername());
            config.setPassword(properties.getPassword());
            config.setDatabase(properties.getDatabase());
            return config;
        }
        String host = url.getHost();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        config.setHostName(host);
        config.setPort(url.getPort() == -1 ? RedisNode.DEFAULT_PORT : url.getPort());
        String userInfo = url.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            if (colon >= 0) {
                config.setUsername(decode(userInfo.substring(0, colon)));
                config.setPassword(decode(userInfo.substring(colon + 1)));
            } else {
                config.setPassword(decode(userInfo));
            }
        }
        String path = url.getPath();
        config.setDatabase(StringUtils.hasText(path) && !"/".equals(path)
                ? Integer.parseInt(path.substring(1)) : 0);
        return config;
    }

    private static String decode(String value) {
        // URI 的加号是普通字符，不使用表单编码的空格替换语义。
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static LettuceClientConfiguration clientConfiguration(DataRedisProperties properties, URI url,
                                                                  boolean clusterMode) {
        DataRedisProperties.Lettuce lettuce = properties.getLettuce();
        DataRedisProperties.Pool pool = lettuce.getPool();
        // 本模块依赖 commons-pool2，未显式禁用时与 Boot 一样默认启用。
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                Boolean.FALSE.equals(pool.getEnabled()) ? LettuceClientConfiguration.builder()
                        : LettucePoolingClientConfiguration.builder().poolConfig(poolConfig(pool));
        if (properties.getSsl().isEnabled() || (url != null && "rediss".equals(url.getScheme()))) {
            builder.useSsl();
        }
        if (properties.getTimeout() != null) {
            builder.commandTimeout(properties.getTimeout());
        }
        if (lettuce.getShutdownTimeout() != null) {
            builder.shutdownTimeout(lettuce.getShutdownTimeout());
        }
        if (StringUtils.hasText(properties.getClientName())) {
            builder.clientName(properties.getClientName());
        }
        ClientOptions.Builder options;
        if (clusterMode) {
            var refreshProperties = lettuce.getCluster().getRefresh();
            var refresh = ClusterTopologyRefreshOptions.builder()
                    .dynamicRefreshSources(refreshProperties.isDynamicRefreshSources());
            if (refreshProperties.getPeriod() != null) {
                refresh.enablePeriodicRefresh(refreshProperties.getPeriod());
            }
            var clusterOptions = ClusterClientOptions.builder().topologyRefreshOptions(refresh.build());
            if (properties.getCluster().getMaxRedirects() != null) {
                clusterOptions.maxRedirects(properties.getCluster().getMaxRedirects());
            }
            options = clusterOptions;
        } else {
            options = ClientOptions.builder();
        }
        if (properties.getConnectTimeout() != null) {
            options.socketOptions(SocketOptions.builder().connectTimeout(properties.getConnectTimeout()).build());
        }
        options.timeoutOptions(TimeoutOptions.enabled());
        builder.clientOptions(options.build());
        return builder.build();
    }

    private static GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig(DataRedisProperties.Pool properties) {
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

    static LettuceConnectionFactory initialize(LettuceConnectionFactory factory) {
        try {
            factory.afterPropertiesSet();
            return factory;
        } catch (RuntimeException | Error failure) {
            try {
                factory.destroy();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }
}
