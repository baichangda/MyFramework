package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

public interface MqttRetainedMessageStore {

    CompletionStage<Void> save(MqttApplicationMessage message);

    CompletionStage<Void> delete(String topicName);

    Collection<MqttApplicationMessage> findMatching(String topicFilter);
}
