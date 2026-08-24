package cn.bcd.app.mqtt.server.connection;

public record MqttConnectionRegistration(
        String clientId,
        MqttConnection connection,
        long generation
) {
}
