package cn.bcd.app.mqtt.server.authentication;

import cn.bcd.app.mqtt.server.config.MqttAuthenticationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 基于配置文件中用户名、明文密码映射的简单认证器。
 *
 * <p>密码使用恒定时间比较，减少根据比较耗时推断密码内容的风险。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "mqtt.server.authentication",
        name = "type",
        havingValue = "simple")
public final class SimpleMqttAuthenticator implements MqttAuthenticator {

    private final Map<String, String> users;

    public SimpleMqttAuthenticator(MqttAuthenticationProperties properties) {
        users = Map.copyOf(properties.getSimple().getUsers());
    }

    @Override
    public boolean authenticate(MqttAuthenticationRequest request) {
        byte[] password = request.password();
        if (request.username() == null || password == null) {
            return false;
        }
        String expectedPassword = users.get(request.username());
        return expectedPassword != null && MessageDigest.isEqual(
                expectedPassword.getBytes(StandardCharsets.UTF_8),
                password);
    }
}
