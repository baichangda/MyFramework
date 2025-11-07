package cn.bcd.lib.spring.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@ConditionalOnProperty(value = "lib.spring.storage.cassandra.dbs")
@Configuration
@EnableConfigurationProperties(CassandraProp.class)
public class CassandraConfig {

    public final static String keySpace = "test1";

    public static CqlSession session;

    @Bean
    public CqlSession cqlSession(CassandraProp cassandraProp) {
        CqlSessionBuilder cqlSessionBuilder = CqlSession.builder()
                .withConfigLoader(
                        DriverConfigLoader.programmaticBuilder()
                                .withInt(DefaultDriverOption.NETTY_IO_SIZE, Runtime.getRuntime().availableProcessors() * 2)
                                .build()
                )
                .withAuthCredentials(cassandraProp.username, cassandraProp.password);
        for (String db : cassandraProp.dbs) {
            String[] split = db.split(":");
            cqlSessionBuilder.addContactPoint(new InetSocketAddress(split[0], Integer.parseInt(split[1])));
        }
        session = cqlSessionBuilder.build();
        return session;
    }
}
