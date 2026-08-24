package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

import java.util.Objects;

public final class MqttPendingPublish {

    private final int packetId;
    private final MqttApplicationMessage message;
    private final boolean retained;
    private volatile boolean sent;

    public MqttPendingPublish(
            int packetId,
            MqttApplicationMessage message,
            boolean retained) {
        this.packetId = packetId;
        this.message = Objects.requireNonNull(message);
        this.retained = retained;
    }

    public int packetId() {
        return packetId;
    }

    public MqttApplicationMessage message() {
        return message;
    }

    public boolean retained() {
        return retained;
    }

    public boolean sent() {
        return sent;
    }

    public void markSent() {
        sent = true;
    }
}
