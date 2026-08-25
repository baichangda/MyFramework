package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

import java.util.Objects;

public record MqttPendingPublish(
        int packetId,
        MqttApplicationMessage message,
        boolean retained,
        boolean sent,
        MqttOutboundPublishState state
) {

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

    public MqttPendingPublish {
        Objects.requireNonNull(message);
        Objects.requireNonNull(state);
    }

    public MqttPendingPublish asSent() {
        return sent ? this : new MqttPendingPublish(
                packetId, message, retained, true, state);
    }

    public MqttPendingPublish waitingForPubComp() {
        return state == MqttOutboundPublishState.WAIT_PUBCOMP
                ? this
                : new MqttPendingPublish(
                        packetId,
                        message,
                        retained,
                        sent,
                        MqttOutboundPublishState.WAIT_PUBCOMP);
    }
}
