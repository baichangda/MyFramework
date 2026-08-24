package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

public record MqttInboundQosTwoPublish(
        int packetId,
        MqttApplicationMessage message,
        boolean retained
) {
}
