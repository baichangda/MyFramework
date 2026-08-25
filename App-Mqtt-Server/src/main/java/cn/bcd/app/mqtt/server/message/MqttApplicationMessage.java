package cn.bcd.app.mqtt.server.message;

import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Objects;

/**
 * 脱离 Netty 缓冲区生命周期的 MQTT 应用消息。
 *
 * <p>载荷在写入和读取时均复制，确保消息可安全地跨线程、排队及持久化。</p>
 *
 * @param topicName 主题名
 * @param payload 消息载荷
 * @param qos 消息发布服务质量等级
 */
public record MqttApplicationMessage(
        String topicName,
        byte[] payload,
        MqttQoS qos
) {
    public MqttApplicationMessage(String topicName, byte[] payload) {
        this(topicName, payload, MqttQoS.AT_MOST_ONCE);
    }

    public MqttApplicationMessage {
        Objects.requireNonNull(topicName);
        payload = Objects.requireNonNull(payload).clone();
        Objects.requireNonNull(qos);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    public boolean isEmpty() {
        return payload.length == 0;
    }

    public int payloadLength() {
        return payload.length;
    }

    /** 返回使用指定投递 QoS 的消息副本。 */
    public MqttApplicationMessage withQos(MqttQoS deliveryQos) {
        return qos == deliveryQos
                ? this
                : new MqttApplicationMessage(topicName, payload, deliveryQos);
    }
}
