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

final class MqttMessageRouter {

    private final MqttClientRegistry clients;
    private final MqttRetainedMessageStore retainedMessageStore;

    MqttMessageRouter(
            MqttClientRegistry clients,
            MqttRetainedMessageStore retainedMessageStore) {
        this.clients = Objects.requireNonNull(clients);
        this.retainedMessageStore = Objects.requireNonNull(retainedMessageStore);
    }

    CompletionStage<Void> publish(MqttApplicationMessage message, boolean retained) {
        if (retained) {
            if (message.isEmpty()) {
                retainedMessageStore.delete(message.topicName());
            } else {
                retainedMessageStore.save(message);
            }
        }

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

    void publishWill(MqttWillMessage willMessage) {
        publish(willMessage.message(), willMessage.retained());
    }

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

    Collection<MqttApplicationMessage> findRetained(String topicFilter) {
        return retainedMessageStore.findMatching(topicFilter);
    }

    private void send(MqttClientRegistry.Delivery delivery) {
        delivery.connection().sendPublish(
                delivery.message(),
                delivery.packetId(),
                delivery.retained(),
                false);
    }
}
