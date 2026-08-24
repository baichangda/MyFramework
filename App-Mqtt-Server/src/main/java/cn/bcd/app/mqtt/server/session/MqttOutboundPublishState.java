package cn.bcd.app.mqtt.server.session;

public enum MqttOutboundPublishState {
    WAIT_PUBACK,
    WAIT_PUBREC,
    WAIT_PUBCOMP
}
