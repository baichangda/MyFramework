package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Objects;

/**
 * 客户端会话中的一条有效订阅。
 *
 * @param topicFilter MQTT 主题过滤器
 * @param qos 客户端请求的最大投递 QoS
 */
public record MqttSubscription(
        String topicFilter,
        MqttQoS qos
) {
    public MqttSubscription {
        Objects.requireNonNull(topicFilter);
        Objects.requireNonNull(qos);
        if (!MqttTopicFilter.isValid(topicFilter)) {
            throw new IllegalArgumentException("Invalid MQTT topic filter: " + topicFilter);
        }
        if (qos == MqttQoS.FAILURE) {
            throw new IllegalArgumentException("Subscription QoS cannot be FAILURE");
        }
    }
}
