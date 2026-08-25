package cn.bcd.app.mqtt.server.connection;

/** 连接关闭原因，用于决定是否发布遗嘱并辅助诊断。 */
public enum MqttConnectionCloseReason {
    NORMAL_DISCONNECT,
    KEEP_ALIVE_TIMEOUT,
    CONNECTION_TAKEN_OVER,
    CONNECTION_REFUSED,
    AUTHORIZATION_FAILED,
    UNSUPPORTED_FEATURE,
    PROTOCOL_ERROR,
    RESOURCE_LIMIT_EXCEEDED,
    INTERNAL_ERROR,
    NETWORK_CLOSED
}
