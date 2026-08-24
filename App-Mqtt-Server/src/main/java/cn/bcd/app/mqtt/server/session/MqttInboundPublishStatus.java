package cn.bcd.app.mqtt.server.session;

public enum MqttInboundPublishStatus {
    STORED,
    DUPLICATE,
    PROTOCOL_ERROR
}
