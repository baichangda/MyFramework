package cn.bcd.app.mqtt.server.session;

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
        subscriptions.put(subscription.topicName(), subscription);
    }

    public Optional<MqttSubscription> findSubscription(String topicName) {
        return Optional.ofNullable(subscriptions.get(topicName));
    }

    public Collection<MqttSubscription> subscriptions() {
        return List.copyOf(subscriptions.values());
    }
}
