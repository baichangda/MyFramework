package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

/**
 * 已收到但尚未通过 PUBREL 释放的 QoS 2 入站消息。
 *
 * @param packetId 报文标识符
 * @param message 应用消息
 * @param retained 原始 PUBLISH 的保留标志
 */
public record MqttInboundQosTwoPublish(
        int packetId,
        MqttApplicationMessage message,
        boolean retained
) {
}
