package cn.bcd.app.mqtt.server.config;

import cn.bcd.app.mqtt.server.authentication.AnonymousMqttAuthenticator;
import cn.bcd.app.mqtt.server.authentication.MqttAuthenticator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MqttAuthenticationConfiguration {

    @Bean
    @ConditionalOnMissingBean(MqttAuthenticator.class)
    @ConditionalOnProperty(
            prefix = "mqtt.server.authentication",
            name = "type",
            havingValue = "anonymous",
            matchIfMissing = true)
    AnonymousMqttAuthenticator anonymousMqttAuthenticator() {
        return new AnonymousMqttAuthenticator();
    }
}
