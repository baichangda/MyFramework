package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;
import cn.bcd.lib.base.exception.BaseException;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MqttSession {

    private final String clientId;
    private final String username;
    private final Map<String, MqttSubscription> subscriptions = new HashMap<>();
    private final Map<Integer, MqttPendingPublish> pendingPublishes = new LinkedHashMap<>();
    private final Map<Integer, MqttInboundQosTwoPublish> inboundQosTwoPublishes =
            new LinkedHashMap<>();
    private int nextPacketId = 1;

    public MqttSession(String clientId) {
        this(clientId, null);
    }

    public MqttSession(String clientId, String username) {
        this.clientId = Objects.requireNonNull(clientId);
        this.username = username;
    }

    public String clientId() {
        return clientId;
    }

    public String username() {
        return username;
    }

    public synchronized boolean subscribe(MqttSubscription subscription) {
        return !subscription.equals(
                subscriptions.put(subscription.topicFilter(), subscription));
    }

    public synchronized boolean unsubscribe(String topicFilter) {
        return subscriptions.remove(topicFilter) != null;
    }

    public synchronized Optional<MqttSubscription> findSubscription(String topicName) {
        return Optional.ofNullable(subscriptions.get(topicName));
    }

    public synchronized Collection<MqttSubscription> subscriptions() {
        return List.copyOf(subscriptions.values());
    }

    public synchronized Optional<MqttQoS> maximumQosMatching(String topicName) {
        MqttQoS maximumQos = null;
        for (MqttSubscription subscription : subscriptions.values()) {
            if (MqttTopicFilter.matches(subscription.topicFilter(), topicName)
                    && (maximumQos == null
                    || subscription.qos().value() > maximumQos.value())) {
                maximumQos = subscription.qos();
            }
        }
        return Optional.ofNullable(maximumQos);
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

    public synchronized boolean acknowledgeQosOne(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending != null && pending.state() == MqttOutboundPublishState.WAIT_PUBACK) {
            pendingPublishes.remove(packetId);
            return true;
        }
        return false;
    }

    public synchronized Optional<MqttPendingPublish> receivePubRec(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending == null || pending.state() == MqttOutboundPublishState.WAIT_PUBACK) {
            return Optional.empty();
        }
        MqttPendingPublish waitingForPubComp = pending.waitingForPubComp();
        pendingPublishes.put(packetId, waitingForPubComp);
        return Optional.of(waitingForPubComp);
    }

    public synchronized boolean receivePubComp(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending != null && pending.state() == MqttOutboundPublishState.WAIT_PUBCOMP) {
            pendingPublishes.remove(packetId);
            return true;
        }
        return false;
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

    public synchronized boolean markPendingPublishSent(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending == null || pending.sent()) {
            return false;
        }
        pendingPublishes.put(packetId, pending.asSent());
        return true;
    }

    public synchronized MqttSessionSnapshot snapshot() {
        return new MqttSessionSnapshot(
                clientId,
                username,
                nextPacketId,
                List.copyOf(subscriptions.values()),
                List.copyOf(pendingPublishes.values()),
                List.copyOf(inboundQosTwoPublishes.values()));
    }

    public static MqttSession restore(MqttSessionSnapshot snapshot) {
        MqttSession session = new MqttSession(snapshot.clientId(), snapshot.username());
        session.nextPacketId = snapshot.nextPacketId();
        for (MqttSubscription subscription : snapshot.subscriptions()) {
            session.subscriptions.put(subscription.topicFilter(), subscription);
        }
        for (MqttPendingPublish pending : snapshot.pendingPublishes()) {
            session.pendingPublishes.put(pending.packetId(), pending);
        }
        for (MqttInboundQosTwoPublish inbound : snapshot.inboundQosTwoPublishes()) {
            session.inboundQosTwoPublishes.put(inbound.packetId(), inbound);
        }
        return session;
    }
}
