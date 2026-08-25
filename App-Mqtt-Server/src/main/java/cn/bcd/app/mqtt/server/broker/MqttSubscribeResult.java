package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

import java.util.Collection;
import java.util.List;

public record MqttSubscribeResult(
        boolean subscribed,
        boolean granted,
        Collection<MqttApplicationMessage> retainedMessages
) {
    public MqttSubscribeResult {
        retainedMessages = List.copyOf(retainedMessages);
    }
}
