package cn.bcd.app.mqtt.server.connection;

public enum MqttConnectionCloseReason {
    NORMAL_DISCONNECT,
    KEEP_ALIVE_TIMEOUT,
    CONNECTION_TAKEN_OVER,
    CONNECTION_REFUSED,
    PROTOCOL_ERROR,
    INTERNAL_ERROR,
    NETWORK_CLOSED
}
