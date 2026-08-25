package cn.bcd.app.mqtt.server.message;

import java.util.Objects;

/**
 * 客户端异常断开时由 Broker 代为发布的遗嘱消息。
 *
 * @param message 应用消息
 * @param retained 是否同时更新对应主题的保留消息
 */
public record MqttWillMessage(
        MqttApplicationMessage message,
        boolean retained
) {

    /**
     * 校验遗嘱消息不能为空。
     *
     * @param message 应用消息
     * @param retained 是否更新保留消息
     */
    public MqttWillMessage {
        Objects.requireNonNull(message);
    }
}
