package cn.bcd.app.mqtt.server.config;

import cn.bcd.app.mqtt.server.authentication.AnonymousMqttAuthenticator;
import cn.bcd.app.mqtt.server.authentication.MqttAuthenticator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 认证器的默认自动配置。
 *
 * <p>未显式配置认证方式或自定义认证器时，注册匿名认证器。</p>
 */
@Configuration(proxyBeanMethods = false)
public class MqttAuthenticationConfiguration {

    /** 在匿名模式下提供默认认证器。 */
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
