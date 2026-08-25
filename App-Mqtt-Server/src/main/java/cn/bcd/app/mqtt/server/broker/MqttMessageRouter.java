package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.message.MqttWillMessage;
import cn.bcd.app.mqtt.server.retained.MqttRetainedMessageStore;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** 根据订阅索引路由应用消息，并维护 MQTT 保留消息。 */
final class MqttMessageRouter {

    private final MqttClientRegistry clients;
    private final MqttRetainedMessageStore retainedMessageStore;

    /**
     * 创建使用指定客户端注册表和保留消息存储的路由器。
     *
     * @param clients 客户端注册表
     * @param retainedMessageStore 保留消息存储
     */
    MqttMessageRouter(
            MqttClientRegistry clients,
            MqttRetainedMessageStore retainedMessageStore) {
        this.clients = Objects.requireNonNull(clients);
        this.retainedMessageStore = Objects.requireNonNull(retainedMessageStore);
    }

    /**
     * 更新可选的保留状态，并将消息投递给全部匹配会话。
     *
     * @param message 应用消息
     * @param retained 是否更新保留消息
     */
    CompletionStage<Void> publish(MqttApplicationMessage message, boolean retained) {
        // RETAIN 且空载荷表示删除保留消息；其他 RETAIN 消息覆盖同主题旧值。
        CompletionStage<Void> retainedWrite = !retained
                ? CompletableFuture.completedFuture(null)
                : message.isEmpty()
                        ? retainedMessageStore.delete(message.topicName())
                        : retainedMessageStore.save(message);
        // 保留状态先落库，再向订阅者投递，避免订阅与发布并发时读到旧保留值。
        return retainedWrite.thenCompose(ignored -> deliver(message));
    }

    /**
     * 并行准备所有订阅者的投递，并等待全部异步操作结束。
     *
     * @param message 应用消息
     */
    private CompletionStage<Void> deliver(MqttApplicationMessage message) {
        // 等待所有会话完成排队/持久化，使发布完成语义覆盖全部目标订阅者。
        List<CompletableFuture<Void>> deliveries = clients.findSubscribers(message.topicName())
                .entrySet()
                .stream()
                .map(entry -> clients.prepareDelivery(
                                entry.getKey(), message, entry.getValue(), false)
                        .thenAccept(delivery -> delivery.ifPresent(this::send))
                        .toCompletableFuture())
                .toList();
        return CompletableFuture.allOf(deliveries.toArray(CompletableFuture[]::new));
    }

    /**
     * 按遗嘱的保留标志发布消息。
     *
     * @param willMessage 遗嘱消息
     */
    void publishWill(MqttWillMessage willMessage) {
        publish(willMessage.message(), willMessage.retained());
    }

    /**
     * 向指定当前连接准备并发送保留消息。
     *
     * @param connection 目标连接
     * @param message 保留消息
     */
    CompletionStage<Boolean> deliverRetained(
            MqttConnection connection,
            MqttApplicationMessage message) {
        if (!clients.isCurrent(connection)) {
            return CompletableFuture.completedFuture(false);
        }
        return clients.prepareDelivery(connection, message, true)
                .thenApply(delivery -> {
                    delivery.ifPresent(this::send);
                    return true;
                });
    }

    /**
     * 查询主题过滤器匹配的保留消息。
     *
     * @param topicFilter 主题过滤器
     */
    Collection<MqttApplicationMessage> findRetained(String topicFilter) {
        return retainedMessageStore.findMatching(topicFilter);
    }

    /**
     * 将已准备好的投递写入目标连接。
     *
     * @param delivery 已准备的投递
     */
    private void send(MqttClientRegistry.Delivery delivery) {
        delivery.connection().sendPublish(
                delivery.message(),
                delivery.packetId(),
                delivery.retained(),
                false);
    }
}
