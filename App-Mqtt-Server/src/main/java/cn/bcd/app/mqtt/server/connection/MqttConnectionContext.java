package cn.bcd.app.mqtt.server.connection;

public record MqttConnectionContext(
        String clientId,
        boolean cleanSession,
        int keepAliveSeconds,
        String username
) {
}
