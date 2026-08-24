package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;
import cn.bcd.lib.base.exception.BaseException;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MqttSession {

    private final String clientId;
    private final ConcurrentMap<String, MqttSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<Integer, MqttPendingPublish> pendingPublishes = new LinkedHashMap<>();
    private int nextPacketId = 1;

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

    public Optional<MqttQoS> maximumQosMatching(String topicName) {
        return subscriptions.values().stream()
                .filter(subscription -> MqttTopicFilter.matches(
                        subscription.topicFilter(), topicName))
                .map(MqttSubscription::qos)
                .max(Comparator.comparingInt(MqttQoS::value));
    }

    public synchronized MqttPendingPublish enqueueQosOne(
            MqttApplicationMessage message,
            boolean retained) {
        for (int attempts = 0; attempts < 65535; attempts++) {
            int packetId = nextPacketId;
            nextPacketId = nextPacketId == 65535 ? 1 : nextPacketId + 1;
            if (!pendingPublishes.containsKey(packetId)) {
                MqttPendingPublish pending = new MqttPendingPublish(
                        packetId, message.withQos(MqttQoS.AT_LEAST_ONCE), retained);
                pendingPublishes.put(packetId, pending);
                return pending;
            }
        }
        throw BaseException.get("No MQTT packet identifier available for clientId[{}]", clientId);
    }

    public synchronized void acknowledge(int packetId) {
        pendingPublishes.remove(packetId);
    }

    public synchronized Collection<MqttPendingPublish> pendingPublishes() {
        return List.copyOf(pendingPublishes.values());
    }
}
