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
    private final Map<Integer, MqttInboundQosTwoPublish> inboundQosTwoPublishes =
            new LinkedHashMap<>();
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

    public synchronized MqttPendingPublish enqueue(
            MqttApplicationMessage message,
            MqttQoS deliveryQos,
            boolean retained) {
        for (int attempts = 0; attempts < 65535; attempts++) {
            int packetId = nextPacketId;
            nextPacketId = nextPacketId == 65535 ? 1 : nextPacketId + 1;
            if (!pendingPublishes.containsKey(packetId)) {
                MqttPendingPublish pending = new MqttPendingPublish(
                        packetId, message.withQos(deliveryQos), retained);
                pendingPublishes.put(packetId, pending);
                return pending;
            }
        }
        throw BaseException.get("No MQTT packet identifier available for clientId[{}]", clientId);
    }

    public synchronized void acknowledgeQosOne(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending != null && pending.state() == MqttOutboundPublishState.WAIT_PUBACK) {
            pendingPublishes.remove(packetId);
        }
    }

    public synchronized Optional<MqttPendingPublish> receivePubRec(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending == null || pending.state() == MqttOutboundPublishState.WAIT_PUBACK) {
            return Optional.empty();
        }
        pending.waitForPubComp();
        return Optional.of(pending);
    }

    public synchronized void receivePubComp(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending != null && pending.state() == MqttOutboundPublishState.WAIT_PUBCOMP) {
            pendingPublishes.remove(packetId);
        }
    }

    public synchronized MqttInboundPublishStatus receiveQosTwo(
            int packetId,
            MqttApplicationMessage message,
            boolean retained,
            boolean duplicate) {
        if (inboundQosTwoPublishes.containsKey(packetId)) {
            return duplicate
                    ? MqttInboundPublishStatus.DUPLICATE
                    : MqttInboundPublishStatus.PROTOCOL_ERROR;
        }
        inboundQosTwoPublishes.put(packetId,
                new MqttInboundQosTwoPublish(packetId, message, retained));
        return MqttInboundPublishStatus.STORED;
    }

    public synchronized Optional<MqttInboundQosTwoPublish> releaseQosTwo(int packetId) {
        return Optional.ofNullable(inboundQosTwoPublishes.remove(packetId));
    }

    public synchronized Collection<MqttPendingPublish> pendingPublishes() {
        return List.copyOf(pendingPublishes.values());
    }
}
