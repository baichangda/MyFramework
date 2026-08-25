package cn.bcd.app.mqtt.server.message;

import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Objects;

public record MqttApplicationMessage(
        String topicName,
        byte[] payload,
        MqttQoS qos
) {
    public MqttApplicationMessage(String topicName, byte[] payload) {
        this(topicName, payload, MqttQoS.AT_MOST_ONCE);
    }

    public MqttApplicationMessage {
        Objects.requireNonNull(topicName);
        payload = Objects.requireNonNull(payload).clone();
        Objects.requireNonNull(qos);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    public boolean isEmpty() {
        return payload.length == 0;
    }

    public int payloadLength() {
        return payload.length;
    }

    public MqttApplicationMessage withQos(MqttQoS deliveryQos) {
        return qos == deliveryQos
                ? this
                : new MqttApplicationMessage(topicName, payload, deliveryQos);
    }
}
