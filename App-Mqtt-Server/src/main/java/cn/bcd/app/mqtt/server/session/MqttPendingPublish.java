package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

import java.util.Objects;

public final class MqttPendingPublish {

    private final int packetId;
    private final MqttApplicationMessage message;
    private final boolean retained;
    private volatile boolean sent;
    private volatile MqttOutboundPublishState state;

    public MqttPendingPublish(
            int packetId,
            MqttApplicationMessage message,
            boolean retained) {
        this(
                packetId,
                message,
                retained,
                false,
                message.qos().value() == 1
                        ? MqttOutboundPublishState.WAIT_PUBACK
                        : MqttOutboundPublishState.WAIT_PUBREC);
    }

    public MqttPendingPublish(
            int packetId,
            MqttApplicationMessage message,
            boolean retained,
            boolean sent,
            MqttOutboundPublishState state) {
        this.packetId = packetId;
        this.message = Objects.requireNonNull(message);
        this.retained = retained;
        this.sent = sent;
        this.state = Objects.requireNonNull(state);
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

    public MqttOutboundPublishState state() {
        return state;
    }

    public void waitForPubComp() {
        state = MqttOutboundPublishState.WAIT_PUBCOMP;
    }

    public MqttPendingPublishSnapshot snapshot() {
        return new MqttPendingPublishSnapshot(
                packetId, message, retained, sent, state);
    }
}
