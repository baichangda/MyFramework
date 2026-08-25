package cn.bcd.app.mqtt.server.session;

public enum MqttInboundPublishStatus {
    STORED,
    DUPLICATE,
    RESOURCE_LIMIT_EXCEEDED,
    PROTOCOL_ERROR
}
