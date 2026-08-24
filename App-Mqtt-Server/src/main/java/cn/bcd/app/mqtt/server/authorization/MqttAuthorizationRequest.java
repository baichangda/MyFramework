package cn.bcd.app.mqtt.server.authorization;

import java.util.Objects;

public record MqttAuthorizationRequest(
        MqttAuthorizationAction action,
        String clientId,
        String username,
        String topic
) {

    public MqttAuthorizationRequest {
        Objects.requireNonNull(action);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(topic);
    }
}
