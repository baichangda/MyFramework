package cn.bcd.lib.spring.redis;

import io.lettuce.core.cluster.ClusterClientOptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RedisConnectionFactoryUtilTest {
    @Test
    void standaloneAppliesCredentialsTimeoutsSslAndClientName() {
        var properties = properties();
        properties.setHost("redis.example");
        properties.setPort(6380);
        properties.setUsername("data-user");
        properties.setPassword("data-password");
        properties.setDatabase(3);
        properties.setTimeout(Duration.ofSeconds(7));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setClientName("test-client");
        properties.getSsl().setEnabled(true);
        properties.getLettuce().setShutdownTimeout(Duration.ofMillis(25));
        var factory = RedisConnectionFactoryUtil.newLettuceConnectionFactory(properties);
        try {
            var standalone = factory.getStandaloneConfiguration();
            assertEquals("redis.example", standalone.getHostName());
            assertEquals(6380, standalone.getPort());
            assertEquals("data-user", standalone.getUsername());
            assertArrayEquals("data-password".toCharArray(), standalone.getPassword().get());
            assertEquals(3, standalone.getDatabase());
            var client = factory.getClientConfiguration();
            assertTrue(client.isUseSsl());
            assertEquals(Duration.ofSeconds(7), client.getCommandTimeout());
            assertEquals(Duration.ofMillis(25), client.getShutdownTimeout());
            assertEquals("test-client", client.getClientName().orElseThrow());
            assertEquals(Duration.ofSeconds(2),
                    client.getClientOptions().orElseThrow().getSocketOptions().getConnectTimeout());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void poolDefaultsToEnabledAndAppliesLimits() {
        var properties = properties();
        var pool = properties.getLettuce().getPool();
        pool.setMaxActive(11);
        pool.setMaxIdle(7);
        pool.setMinIdle(2);
        pool.setMaxWait(Duration.ofSeconds(3));
        var factory = RedisConnectionFactoryUtil.newLettuceConnectionFactory(properties);
        try {
            var client = assertInstanceOf(LettucePoolingClientConfiguration.class, factory.getClientConfiguration());
            assertEquals(11, client.getPoolConfig().getMaxTotal());
            assertEquals(7, client.getPoolConfig().getMaxIdle());
            assertEquals(2, client.getPoolConfig().getMinIdle());
            assertEquals(Duration.ofSeconds(3), client.getPoolConfig().getMaxWaitDuration());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void poolCanBeExplicitlyDisabled() {
        var properties = properties();
        properties.getLettuce().getPool().setEnabled(false);
        var factory = RedisConnectionFactoryUtil.newLettuceConnectionFactory(properties);
        try {
            assertFalse(factory.getClientConfiguration() instanceof LettucePoolingClientConfiguration);
        } finally {
            factory.destroy();
        }
    }

    @Test
    void urlOverridesStandalonePropertiesAndDecodesCredentials() {
        var properties = properties();
        properties.setHost("ignored-host");
        properties.setUsername("ignored-user");
        properties.setPassword("ignored-password");
        properties.setDatabase(8);
        properties.setUrl("rediss://user%3Aname:p%2B%40%3Ass@[::1]:6380/4");
        var factory = RedisConnectionFactoryUtil.newLettuceConnectionFactory(properties);
        try {
            var standalone = factory.getStandaloneConfiguration();
            assertEquals("::1", standalone.getHostName());
            assertEquals(6380, standalone.getPort());
            assertEquals("user:name", standalone.getUsername());
            assertArrayEquals("p+@:ss".toCharArray(), standalone.getPassword().get());
            assertEquals(4, standalone.getDatabase());
            assertTrue(factory.getClientConfiguration().isUseSsl());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void urlWithoutCredentialsOrPortUsesUrlDefaults() {
        var properties = properties();
        properties.setUsername("ignored-user");
        properties.setPassword("ignored-password");
        properties.setDatabase(8);
        properties.setUrl("redis://localhost");
        var factory = RedisConnectionFactoryUtil.newLettuceConnectionFactory(properties);
        try {
            var standalone = factory.getStandaloneConfiguration();
            assertEquals(6379, standalone.getPort());
            assertEquals(0, standalone.getDatabase());
            assertNull(standalone.getUsername());
            assertFalse(standalone.getPassword().isPresent());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void sentinelTakesPrecedenceAndKeepsSentinelAndDataCredentialsSeparate() {
        var properties = properties();
        properties.setUsername("data-user");
        properties.setPassword("data-password");
        properties.setDatabase(5);
        var sentinel = new DataRedisProperties.Sentinel();
        sentinel.setMaster("mymaster");
        sentinel.setNodes(List.of("[::1]:26380", "sentinel.example:26379"));
        sentinel.setUsername("sentinel-user");
        sentinel.setPassword("sentinel-password");
        properties.setSentinel(sentinel);
        properties.setCluster(new DataRedisProperties.Cluster());
        var factory = RedisConnectionFactoryUtil.newLettuceConnectionFactory(properties);
        try {
            assertNull(factory.getClusterConfiguration());
            var config = factory.getSentinelConfiguration();
            assertNotNull(config);
            assertEquals("mymaster", config.getMaster().getName());
            assertTrue(config.getSentinels().contains(new RedisNode("::1", 26380)));
            assertEquals("data-user", config.getUsername());
            assertArrayEquals("data-password".toCharArray(), config.getPassword().get());
            assertEquals("sentinel-user", config.getSentinelUsername());
            assertArrayEquals("sentinel-password".toCharArray(), config.getSentinelPassword().get());
            assertEquals(5, config.getDatabase());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void clusterParsesIpv6AndAppliesRedirectsAndTopologyRefresh() {
        var properties = properties();
        var cluster = new DataRedisProperties.Cluster();
        cluster.setNodes(List.of("[::1]:6380", "redis.example:6381"));
        cluster.setMaxRedirects(8);
        properties.setCluster(cluster);
        var refresh = properties.getLettuce().getCluster().getRefresh();
        refresh.setDynamicRefreshSources(false);
        refresh.setPeriod(Duration.ofSeconds(15));
        var factory = RedisConnectionFactoryUtil.newLettuceConnectionFactory(properties);
        try {
            var config = factory.getClusterConfiguration();
            assertNotNull(config);
            assertTrue(config.getClusterNodes().contains(new RedisNode("::1", 6380)));
            assertEquals(8, config.getMaxRedirects());
            var options = assertInstanceOf(ClusterClientOptions.class,
                    factory.getClientConfiguration().getClientOptions().orElseThrow());
            assertEquals(8, options.getMaxRedirects());
            assertFalse(options.getTopologyRefreshOptions().useDynamicRefreshSources());
            assertTrue(options.getTopologyRefreshOptions().isPeriodicRefreshEnabled());
            assertEquals(Duration.ofSeconds(15), options.getTopologyRefreshOptions().getRefreshPeriod());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void clusterAllowsUnspecifiedMaxRedirects() {
        var properties = properties();
        var cluster = new DataRedisProperties.Cluster();
        cluster.setNodes(List.of("redis.example:6379"));
        properties.setCluster(cluster);
        var factory = RedisConnectionFactoryUtil.newLettuceConnectionFactory(properties);
        try {
            assertNotNull(factory.getClusterConfiguration());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void invalidConfigurationFailsBeforeStartingAClient() {
        var properties = properties();
        properties.setUrl("http://localhost:6379");
        assertThrows(IllegalArgumentException.class,
                () -> RedisConnectionFactoryUtil.newLettuceConnectionFactory(properties));
        properties.setUrl(null);
        properties.setCluster(new DataRedisProperties.Cluster());
        assertThrows(IllegalArgumentException.class,
                () -> RedisConnectionFactoryUtil.newLettuceConnectionFactory(properties));
        assertThrows(NullPointerException.class,
                () -> RedisConnectionFactoryUtil.newLettuceConnectionFactory(null));
    }

    @Test
    void initializationFailureDestroysFactoryAndPreservesCleanupFailure() {
        var failure = new IllegalStateException("initialization failed");
        var cleanupFailure = new IllegalArgumentException("cleanup failed");
        AtomicBoolean destroyed = new AtomicBoolean();
        LettuceConnectionFactory factory = new LettuceConnectionFactory() {
            @Override
            public void afterPropertiesSet() {
                throw failure;
            }

            @Override
            public void destroy() {
                destroyed.set(true);
                throw cleanupFailure;
            }
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> RedisConnectionFactoryUtil.initialize(factory)));
        assertTrue(destroyed.get());
        assertArrayEquals(new Throwable[]{cleanupFailure}, failure.getSuppressed());
    }

    @Test
    void initializationErrorAlsoDestroysFactory() {
        var failure = new AssertionError("initialization failed");
        AtomicBoolean destroyed = new AtomicBoolean();
        LettuceConnectionFactory factory = new LettuceConnectionFactory() {
            @Override
            public void afterPropertiesSet() {
                throw failure;
            }

            @Override
            public void destroy() {
                destroyed.set(true);
            }
        };
        assertSame(failure, assertThrows(AssertionError.class,
                () -> RedisConnectionFactoryUtil.initialize(factory)));
        assertTrue(destroyed.get());
    }

    private static DataRedisProperties properties() {
        var properties = new DataRedisProperties();
        properties.getLettuce().setShutdownTimeout(Duration.ZERO);
        return properties;
    }
}
