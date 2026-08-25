package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.session.MqttSession;

public record MqttConnectResult(
        MqttSession session,
        boolean sessionPresent,
        boolean accepted
) {
    static MqttConnectResult accepted(MqttSession session, boolean sessionPresent) {
        return new MqttConnectResult(session, sessionPresent, true);
    }

    static MqttConnectResult rejected() {
        return new MqttConnectResult(null, false, false);
    }
}
