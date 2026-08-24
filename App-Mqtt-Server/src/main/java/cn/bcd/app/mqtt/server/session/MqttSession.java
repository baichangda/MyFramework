package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MqttSession {

    private final String clientId;
    private final ConcurrentMap<String, MqttSubscription> subscriptions = new ConcurrentHashMap<>();

    public MqttSession(String clientId) {
        this.clientId = Objects.requireNonNull(clientId);
    }

    public String clientId() {
        return clientId;
    }

    public void subscribe(MqttSubscription subscription) {
        subscriptions.put(subscription.topicFilter(), subscription);
    }

    public Optional<MqttSubscription> findSubscription(String topicName) {
        return Optional.ofNullable(subscriptions.get(topicName));
    }

    public Collection<MqttSubscription> subscriptions() {
        return List.copyOf(subscriptions.values());
    }

    public boolean hasSubscriptionMatching(String topicName) {
        return subscriptions.values().stream()
                .anyMatch(subscription -> MqttTopicFilter.matches(
                        subscription.topicFilter(), topicName));
    }
}
