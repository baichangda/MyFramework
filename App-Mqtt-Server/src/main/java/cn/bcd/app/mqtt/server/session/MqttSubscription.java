package cn.bcd.app.mqtt.server.session;

import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Objects;

public record MqttSubscription(
        String topicName,
        MqttQoS qos
) {
    public MqttSubscription {
        Objects.requireNonNull(topicName);
        Objects.requireNonNull(qos);
    }
}
