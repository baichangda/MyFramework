package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.config.MqttResourceLimits;
import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** 仅保存在进程内的保留消息存储，适用于测试或无需跨重启恢复的场景。 */
@Component
@ConditionalOnProperty(
        prefix = "mqtt.server.persistence.retained-message",
        name = "type",
        havingValue = "memory")
public final class InMemoryMqttRetainedMessageStore implements MqttRetainedMessageStore {

    private final MqttRetainedMessageIndex index;

    public InMemoryMqttRetainedMessageStore() {
        this(MqttResourceLimits.defaults());
    }

    @Autowired
    public InMemoryMqttRetainedMessageStore(MqttServerProperties properties) {
        this(MqttResourceLimits.from(properties.getLimits()));
    }

    private InMemoryMqttRetainedMessageStore(MqttResourceLimits limits) {
        index = new MqttRetainedMessageIndex(
                limits.retainedMessages(), limits.retainedMessageBytes());
    }

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
