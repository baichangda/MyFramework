package cn.bcd.app.mqtt.server.message;

import java.util.Objects;

public record MqttWillMessage(
        MqttApplicationMessage message,
        boolean retained
) {

    public MqttWillMessage {
        Objects.requireNonNull(message);
    }
}
