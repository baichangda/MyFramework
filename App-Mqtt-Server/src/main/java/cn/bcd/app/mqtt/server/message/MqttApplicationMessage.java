package cn.bcd.app.mqtt.server.message;

import java.util.Objects;

public record MqttApplicationMessage(
        String topicName,
        byte[] payload
) {
    public MqttApplicationMessage {
        Objects.requireNonNull(topicName);
        payload = Objects.requireNonNull(payload).clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    public boolean isEmpty() {
        return payload.length == 0;
    }
}
