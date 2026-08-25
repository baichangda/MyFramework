package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.session.MqttSession;

/**
 * Broker 注册连接的结果。
 *
 * @param session 接受连接后绑定的会话
 * @param sessionPresent CONNACK 是否应设置 Session Present
 * @param accepted 是否接受连接
 */
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
