package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.message.MqttWillMessage;

public record MqttConnectionContext(
        String clientId,
        boolean cleanSession,
        int keepAliveSeconds,
        String username,
        MqttWillMessage willMessage
) {
}
