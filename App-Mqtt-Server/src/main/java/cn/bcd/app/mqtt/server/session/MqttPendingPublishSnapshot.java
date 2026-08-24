package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

import java.util.Objects;

public record MqttPendingPublishSnapshot(
        int packetId,
        MqttApplicationMessage message,
        boolean retained,
        boolean sent,
        MqttOutboundPublishState state
) {

    public MqttPendingPublishSnapshot {
        Objects.requireNonNull(message);
        Objects.requireNonNull(state);
    }
}
