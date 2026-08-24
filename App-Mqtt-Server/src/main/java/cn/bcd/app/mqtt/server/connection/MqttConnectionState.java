package cn.bcd.app.mqtt.server.connection;

public enum MqttConnectionState {
    NEW,
    CONNECTED,
    CLOSING,
    CLOSED
}
