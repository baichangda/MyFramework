package cn.bcd.app.mqtt.server.session;

/** QoS 2 入站 PUBLISH 报文的登记结果。 */
public enum MqttInboundPublishStatus {
    /** 报文已保存，等待 PUBREL。 */
    STORED,
    /** DUP 标志正确的重复报文，沿用已有状态。 */
    DUPLICATE,
    /** 会话的入站飞行中消息已达到上限。 */
    RESOURCE_LIMIT_EXCEEDED,
    /** 报文与当前状态冲突，属于协议错误。 */
    PROTOCOL_ERROR
}
