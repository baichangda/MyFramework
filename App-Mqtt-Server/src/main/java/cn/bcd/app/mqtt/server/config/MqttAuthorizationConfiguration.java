package cn.bcd.app.mqtt.server.config;

import cn.bcd.app.mqtt.server.authorization.AllowAllMqttAuthorizer;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 授权器的默认自动配置。
 *
 * <p>未显式配置授权方式或自定义授权器时，注册全量放行授权器。</p>
 */
@Configuration(proxyBeanMethods = false)
public class MqttAuthorizationConfiguration {

    @Bean
    @ConditionalOnMissingBean(MqttAuthorizer.class)
    @ConditionalOnProperty(
            prefix = "mqtt.server.authorization",
            name = "type",
            havingValue = "allow-all",
            matchIfMissing = true)
    AllowAllMqttAuthorizer allowAllMqttAuthorizer() {
        return new AllowAllMqttAuthorizer();
    }
}
