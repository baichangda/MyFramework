package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(
        prefix = "mqtt.server.persistence.retained-message",
        name = "type",
        havingValue = "memory")
public final class InMemoryMqttRetainedMessageStore implements MqttRetainedMessageStore {

    private final ConcurrentMap<String, MqttApplicationMessage> messages = new ConcurrentHashMap<>();

    @Override
    public void save(MqttApplicationMessage message) {
        messages.put(message.topicName(), message);
    }

    @Override
    public void delete(String topicName) {
        messages.remove(topicName);
    }

    @Override
    public Collection<MqttApplicationMessage> findMatching(String topicFilter) {
        return messages.values().stream()
                .filter(message -> MqttTopicFilter.matches(topicFilter, message.topicName()))
                .toList();
    }
}
