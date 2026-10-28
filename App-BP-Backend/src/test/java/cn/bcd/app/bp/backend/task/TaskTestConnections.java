package cn.bcd.app.bp.backend.task;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.lettuce.core.resource.DefaultClientResources;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

/** 集成测试读取 application.yml 的连接配置，不启动应用或其后台 Worker。 */
final class TaskTestConnections {
    private static final Map<String, Object> SETTINGS = load();

    private TaskTestConnections() {}

    static HikariDataSource mysql(String database) {
        Map<String, Object> settings = section("spring", "datasource");
        String url = settings.get("url").toString();
        if (database != null) {
            int start = url.indexOf('/', "jdbc:mysql://".length()) + 1;
            int query = url.indexOf('?', start);
            url = url.substring(0, start) + database + (query < 0 ? "" : url.substring(query));
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(settings.get("username").toString());
        config.setPassword(settings.get("password").toString());
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(10000);
        return new HikariDataSource(config);
    }

    static LettuceConnectionFactory redis() {
        Map<String, Object> settings = section("spring", "data", "redis");
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                settings.get("host").toString(), Integer.parseInt(settings.get("port").toString()));
        config.setDatabase(Integer.parseInt(settings.getOrDefault("database", 0).toString()));
        if (settings.containsKey("username")) {
            config.setUsername(settings.get("username").toString());
        }
        if (settings.containsKey("password")) {
            config.setPassword(settings.get("password").toString());
        }
        var client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(10)).shutdownTimeout(Duration.ofMillis(100));
        // 与 JDBC 一样使用系统 DNS，避免 Netty 自行发送 DNS 请求绕过宿主机解析配置。
        client.clientResources(DefaultClientResources.builder()
                .addressResolverGroup(DefaultAddressResolverGroup.INSTANCE).build());
        if (Boolean.TRUE.equals(section("spring", "data", "redis", "ssl").get("enabled"))) {
            client.useSsl();
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, client.build());
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(String... path) {
        Map<String, Object> result = SETTINGS;
        for (String key : path) {
            result = (Map<String, Object>) result.get(key);
        }
        return result;
    }

    private static Map<String, Object> load() {
        try (InputStream input = TaskTestConnections.class.getResourceAsStream("/application.yml")) {
            if (input == null) {
                throw new IllegalStateException("application.yml is missing");
            }
            return new Yaml().load(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read application.yml", ex);
        }
    }
}
