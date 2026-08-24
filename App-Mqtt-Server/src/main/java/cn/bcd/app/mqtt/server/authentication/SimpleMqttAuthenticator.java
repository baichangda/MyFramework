package cn.bcd.app.mqtt.server.authentication;

import cn.bcd.app.mqtt.server.config.MqttAuthenticationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

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
