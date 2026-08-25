package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.message.MqttWillMessage;
import cn.bcd.app.mqtt.server.retained.MqttRetainedMessageStore;

import java.util.Collection;
import java.util.Objects;

final class MqttMessageRouter {

    private final MqttClientRegistry clients;
    private final MqttRetainedMessageStore retainedMessageStore;

    MqttMessageRouter(
            MqttClientRegistry clients,
            MqttRetainedMessageStore retainedMessageStore) {
        this.clients = Objects.requireNonNull(clients);
        this.retainedMessageStore = Objects.requireNonNull(retainedMessageStore);
    }

    void publish(MqttApplicationMessage message, boolean retained) {
        if (retained) {
            if (message.isEmpty()) {
                retainedMessageStore.delete(message.topicName());
            } else {
                retainedMessageStore.save(message);
            }
        }

        for (String clientId : clients.findSubscribers(message.topicName())) {
            clients.prepareDelivery(clientId, message, false)
                    .ifPresent(this::send);
        }
    }

    void publishWill(MqttWillMessage willMessage) {
        publish(willMessage.message(), willMessage.retained());
    }

    boolean deliverRetained(
            MqttConnection connection,
            MqttApplicationMessage message) {
        if (!clients.isCurrent(connection)) {
            return false;
        }
        clients.prepareDelivery(connection, message, true)
                .ifPresent(this::send);
        return true;
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
