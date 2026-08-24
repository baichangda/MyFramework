package cn.bcd.app.mqtt.server.authentication;

import java.util.Objects;

public record MqttAuthenticationRequest(
        String clientId,
        String username,
        byte[] password
) {

    public MqttAuthenticationRequest {
        Objects.requireNonNull(clientId);
        password = password == null ? null : password.clone();
    }

    @Override
    public byte[] password() {
        return password == null ? null : password.clone();
    }
}
