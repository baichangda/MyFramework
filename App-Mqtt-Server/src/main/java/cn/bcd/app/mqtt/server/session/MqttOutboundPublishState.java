package cn.bcd.app.mqtt.server.session;

/** Broker 向客户端投递消息时等待的下一种确认报文。 */
public enum MqttOutboundPublishState {
    /** QoS 1：等待 PUBACK。 */
    WAIT_PUBACK,
    /** QoS 2 第一阶段：等待 PUBREC。 */
    WAIT_PUBREC,
    /** QoS 2 第二阶段：已发送 PUBREL，等待 PUBCOMP。 */
    WAIT_PUBCOMP
}
