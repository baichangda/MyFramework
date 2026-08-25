package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.lib.base.exception.BaseException;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Collection;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 单个客户端的 MQTT 会话状态。
 *
 * <p>该对象可能同时被 Netty 事件循环、持久化回调和消息路由线程访问，所有可变状态
 * 均通过同步方法保护。返回集合时一律创建快照，不向调用方暴露内部容器。</p>
 */
public final class MqttSession {

    private final String clientId;
    private final String username;
    private final int maxSubscriptions;
    private final int maxPendingMessages;
    private final int maxInboundInflightMessages;
    private final Map<String, MqttSubscription> subscriptions = new HashMap<>();
    private final Map<Integer, MqttPendingPublish> pendingPublishes = new LinkedHashMap<>();
    private final Map<Integer, MqttInboundQosTwoPublish> inboundQosTwoPublishes =
            new LinkedHashMap<>();
    private final BitSet packetIdentifiers = new BitSet(65536);
    private long pendingPayloadBytes;
    private int nextPacketId = 1;

    public MqttSession(String clientId) {
        this(clientId, null);
    }

    public MqttSession(String clientId, String username) {
        this(clientId, username, Integer.MAX_VALUE, 65535);
    }

    public MqttSession(
            String clientId,
            String username,
            int maxSubscriptions,
            int maxPendingMessages) {
        this(clientId, username, maxSubscriptions, maxPendingMessages, maxPendingMessages);
    }

    public MqttSession(
            String clientId,
            String username,
            int maxSubscriptions,
            int maxPendingMessages,
            int maxInboundInflightMessages) {
        this.clientId = Objects.requireNonNull(clientId);
        this.username = username;
        this.maxSubscriptions = maxSubscriptions;
        this.maxPendingMessages = maxPendingMessages;
        this.maxInboundInflightMessages = maxInboundInflightMessages;
    }

    public String clientId() {
        return clientId;
    }

    public String username() {
        return username;
    }

    public synchronized boolean subscribe(MqttSubscription subscription) {
        if (!subscriptions.containsKey(subscription.topicFilter())
                && subscriptions.size() >= maxSubscriptions) {
            return false;
        }
        return !subscription.equals(
                subscriptions.put(subscription.topicFilter(), subscription));
    }

    public synchronized boolean canSubscribe(String topicFilter) {
        return subscriptions.containsKey(topicFilter)
                || subscriptions.size() < maxSubscriptions;
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

    public synchronized MqttPendingPublish enqueue(
            MqttApplicationMessage message,
            MqttQoS deliveryQos,
            boolean retained) {
        if (pendingPublishes.size() >= maxPendingMessages) {
            throw BaseException.get(
                    "MQTT inflight message limit exceeded for clientId[{}]", clientId);
        }
        // 优先从游标之后分配，达到 65535 后再从 1 开始寻找空闲标识符。
        int packetId = packetIdentifiers.nextClearBit(nextPacketId);
        if (packetId > 65535) {
            packetId = packetIdentifiers.nextClearBit(1);
        }
        if (packetId > 65535) {
            throw BaseException.get("No MQTT packet identifier available for clientId[{}]", clientId);
        }
        packetIdentifiers.set(packetId);
        // 游标只决定下一次搜索起点；已占用标识符仍由 BitSet 保证不会重复分配。
        nextPacketId = packetId == 65535 ? 1 : packetId + 1;
        MqttPendingPublish pending = new MqttPendingPublish(
                packetId, message.withQos(deliveryQos), retained);
        pendingPublishes.put(packetId, pending);
        pendingPayloadBytes += pending.message().payloadLength();
        return pending;
    }

    public synchronized boolean acknowledgeQosOne(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending != null && pending.state() == MqttOutboundPublishState.WAIT_PUBACK) {
            pendingPublishes.remove(packetId);
            packetIdentifiers.clear(packetId);
            pendingPayloadBytes -= pending.message().payloadLength();
            return true;
        }
        return false;
    }

    public synchronized Optional<MqttPendingPublish> receivePubRec(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending == null || pending.state() == MqttOutboundPublishState.WAIT_PUBACK) {
            return Optional.empty();
        }
        // 重复 PUBREC 也返回 WAIT_PUBCOMP 状态，使上层可以安全地重发 PUBREL。
        MqttPendingPublish waitingForPubComp = pending.waitingForPubComp();
        pendingPublishes.put(packetId, waitingForPubComp);
        return Optional.of(waitingForPubComp);
    }

    public synchronized boolean receivePubComp(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending != null && pending.state() == MqttOutboundPublishState.WAIT_PUBCOMP) {
            pendingPublishes.remove(packetId);
            packetIdentifiers.clear(packetId);
            pendingPayloadBytes -= pending.message().payloadLength();
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
            // 只有设置 DUP 的重传才允许复用现有 packetId。
            return duplicate
                    ? MqttInboundPublishStatus.DUPLICATE
                    : MqttInboundPublishStatus.PROTOCOL_ERROR;
        }
        if (inboundQosTwoPublishes.size() >= maxInboundInflightMessages) {
            return MqttInboundPublishStatus.RESOURCE_LIMIT_EXCEEDED;
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

    public synchronized Optional<MqttPendingPublish> pendingPublish(int packetId) {
        return Optional.ofNullable(pendingPublishes.get(packetId));
    }

    public synchronized int nextPacketId() {
        return nextPacketId;
    }

    public synchronized int pendingPublishCount() {
        return pendingPublishes.size();
    }

    public synchronized long pendingPayloadBytes() {
        return pendingPayloadBytes;
    }

    public synchronized boolean canEnqueue() {
        return pendingPublishes.size() < maxPendingMessages;
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
        return restore(snapshot, Integer.MAX_VALUE, 65535);
    }

    public static MqttSession restore(
            MqttSessionSnapshot snapshot,
            int maxSubscriptions,
            int maxPendingMessages) {
        return restore(snapshot, maxSubscriptions, maxPendingMessages, maxPendingMessages);
    }

    public static MqttSession restore(
            MqttSessionSnapshot snapshot,
            int maxSubscriptions,
            int maxPendingMessages,
            int maxInboundInflightMessages) {
        MqttSession session = new MqttSession(
                snapshot.clientId(),
                snapshot.username(),
                maxSubscriptions,
                maxPendingMessages,
                maxInboundInflightMessages);
        session.nextPacketId = snapshot.nextPacketId();
        for (MqttSubscription subscription : snapshot.subscriptions()) {
            session.subscriptions.put(subscription.topicFilter(), subscription);
        }
        for (MqttPendingPublish pending : snapshot.pendingPublishes()) {
            session.pendingPublishes.put(pending.packetId(), pending);
            // 恢复占用位和载荷计数，防止重启后重复分配 packetId 或绕过资源限制。
            session.packetIdentifiers.set(pending.packetId());
            session.pendingPayloadBytes += pending.message().payloadLength();
        }
        for (MqttInboundQosTwoPublish inbound : snapshot.inboundQosTwoPublishes()) {
            session.inboundQosTwoPublishes.put(inbound.packetId(), inbound);
        }
        return session;
    }
}
