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

    /**
     * 使用服务配置中的资源上限创建内存存储。
     *
     * @param properties 服务配置
     */
    @Autowired
    public InMemoryMqttRetainedMessageStore(MqttServerProperties properties) {
        this(MqttResourceLimits.from(properties.getLimits()));
    }

    /**
     * 创建使用指定不可变资源上限的内存存储。
     *
     * @param limits 资源上限
     */
    private InMemoryMqttRetainedMessageStore(MqttResourceLimits limits) {
        index = new MqttRetainedMessageIndex(
                limits.retainedMessages(), limits.retainedMessageBytes());
    }

    /**
     * 将消息写入内存主题索引。
     *
     * @param message 保留消息
     */
    @Override
    public CompletionStage<Void> save(MqttApplicationMessage message) {
        index.put(message);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 从内存主题索引删除指定主题。
     *
     * @param topicName 主题名
     */
    @Override
    public CompletionStage<Void> delete(String topicName) {
        index.remove(topicName);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 查询过滤器匹配的保留消息快照。
     *
     * @param topicFilter 主题过滤器
     */
    @Override
    public Collection<MqttApplicationMessage> findMatching(String topicFilter) {
        return index.findMatching(topicFilter);
    }
}
