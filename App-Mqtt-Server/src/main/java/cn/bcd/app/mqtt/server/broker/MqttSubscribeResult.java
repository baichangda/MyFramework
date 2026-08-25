package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

import java.util.Collection;
import java.util.List;

/**
 * 单条主题过滤器的订阅结果。
 *
 * @param subscribed 当前连接是否仍是 clientId 对应的有效连接
 * @param granted 是否授予订阅
 * @param retainedMessages 授予订阅后首次需要投递的保留消息
 */
public record MqttSubscribeResult(
        boolean subscribed,
        boolean granted,
        Collection<MqttApplicationMessage> retainedMessages
) {
    public MqttSubscribeResult {
        retainedMessages = List.copyOf(retainedMessages);
    }
}
