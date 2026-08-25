package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@ConditionalOnProperty(
        prefix = "mqtt.server.persistence.retained-message",
        name = "type",
        havingValue = "memory")
public final class InMemoryMqttRetainedMessageStore implements MqttRetainedMessageStore {

    private final MqttRetainedMessageIndex index = new MqttRetainedMessageIndex();

    @Override
    public CompletionStage<Void> save(MqttApplicationMessage message) {
        index.put(message);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> delete(String topicName) {
        index.remove(topicName);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Collection<MqttApplicationMessage> findMatching(String topicFilter) {
        return index.findMatching(topicFilter);
    }
}
