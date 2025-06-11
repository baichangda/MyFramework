package cn.bcd.lib.storage.iotdb;

import org.apache.iotdb.session.pool.SessionPool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(prefix = "lib.storage.iotdb.urls")
@EnableConfigurationProperties(IotdbProp.class)
@Configuration
public class IotdbConfig {

    public static SessionPool session;

    @Bean
    public SessionPool sessionPool(IotdbProp iotdbProp) {
        session = new SessionPool.Builder()
                .nodeUrls(iotdbProp.urls)
                .user(iotdbProp.user)
                .password(iotdbProp.password)
                .maxSize(iotdbProp.maxSize)
                .build();
        return session;
    }
}
