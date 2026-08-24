package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.session.MqttSession;

public record MqttConnectResult(
        MqttSession session,
        boolean sessionPresent
) {
}
