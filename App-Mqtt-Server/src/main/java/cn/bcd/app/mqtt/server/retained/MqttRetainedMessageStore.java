package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

import java.util.Collection;

public interface MqttRetainedMessageStore {

    void save(MqttApplicationMessage message);

    void delete(String topicName);

    Collection<MqttApplicationMessage> findMatching(String topicFilter);
}
