package cn.bcd.app.mqtt.server.session;

import java.util.Objects;

public final class MqttSession {

    private final String clientId;

    public MqttSession(String clientId) {
        this.clientId = Objects.requireNonNull(clientId);
    }

    public String clientId() {
        return clientId;
    }
}
