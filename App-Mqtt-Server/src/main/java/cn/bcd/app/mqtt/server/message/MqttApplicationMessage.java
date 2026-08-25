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
    /**
     * 创建默认 QoS 0 的应用消息。
     *
     * @param topicName 主题名
     * @param payload 消息载荷
     */
    public MqttApplicationMessage(String topicName, byte[] payload) {
        this(topicName, payload, MqttQoS.AT_MOST_ONCE);
    }

    /**
     * 校验消息属性并复制载荷。
     *
     * @param topicName 主题名
     * @param payload 消息载荷
     * @param qos 服务质量等级
     */
    public MqttApplicationMessage {
        Objects.requireNonNull(topicName);
        payload = Objects.requireNonNull(payload).clone();
        Objects.requireNonNull(qos);
    }

    /** 返回载荷副本，避免调用方修改消息内部数据。 */
    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /** 判断消息是否不包含载荷字节。 */
    public boolean isEmpty() {
        return payload.length == 0;
    }

    public int payloadLength() {
        return payload.length;
    }

    /**
     * 返回使用指定投递 QoS 的消息副本。
     *
     * @param deliveryQos 投递服务质量等级
     */
    public MqttApplicationMessage withQos(MqttQoS deliveryQos) {
        return qos == deliveryQos
                ? this
                : new MqttApplicationMessage(topicName, payload, deliveryQos);
    }
}
