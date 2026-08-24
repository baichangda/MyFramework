package cn.bcd.app.mqtt.server.config;

import cn.bcd.app.mqtt.server.authorization.AllowAllMqttAuthorizer;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
