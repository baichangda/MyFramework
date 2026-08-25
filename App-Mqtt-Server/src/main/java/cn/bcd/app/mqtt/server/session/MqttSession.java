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

    /**
     * 创建匿名身份且使用宽松默认上限的会话。
     *
     * @param clientId 客户端标识
     */
    public MqttSession(String clientId) {
        this(clientId, null);
    }

    /**
     * 创建使用宽松默认上限的会话。
     *
     * @param clientId 客户端标识
     * @param username 用户名
     */
    public MqttSession(String clientId, String username) {
        this(clientId, username, Integer.MAX_VALUE, 65535);
    }

    /**
     * 创建入站、出站共用待确认消息上限的会话。
     *
     * @param clientId 客户端标识
     * @param username 用户名
     * @param maxSubscriptions 订阅数量上限
     * @param maxPendingMessages 待确认消息数量上限
     */
    public MqttSession(
            String clientId,
            String username,
            int maxSubscriptions,
            int maxPendingMessages) {
        this(clientId, username, maxSubscriptions, maxPendingMessages, maxPendingMessages);
    }

    /**
     * 创建使用完整资源限制的会话。
     *
     * @param clientId 客户端标识
     * @param username 用户名
     * @param maxSubscriptions 订阅数量上限
     * @param maxPendingMessages 出站待确认消息数量上限
     * @param maxInboundInflightMessages 入站 QoS 2 消息数量上限
     */
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

    /**
     * 新增或更新订阅；新过滤器超过数量上限时返回失败。
     *
     * @param subscription 订阅内容
     */
    public synchronized boolean subscribe(MqttSubscription subscription) {
        if (!subscriptions.containsKey(subscription.topicFilter())
                && subscriptions.size() >= maxSubscriptions) {
            return false;
        }
        return !subscription.equals(
                subscriptions.put(subscription.topicFilter(), subscription));
    }

    /**
     * 判断新增或更新指定过滤器是否会超过订阅上限。
     *
     * @param topicFilter 主题过滤器
     */
    public synchronized boolean canSubscribe(String topicFilter) {
        return subscriptions.containsKey(topicFilter)
                || subscriptions.size() < maxSubscriptions;
    }

    /**
     * 删除指定主题过滤器的订阅。
     *
     * @param topicFilter 主题过滤器
     */
    public synchronized boolean unsubscribe(String topicFilter) {
        return subscriptions.remove(topicFilter) != null;
    }

    /**
     * 按过滤器键查找会话订阅。
     *
     * @param topicName 订阅过滤器键
     */
    public synchronized Optional<MqttSubscription> findSubscription(String topicName) {
        return Optional.ofNullable(subscriptions.get(topicName));
    }

    /** 返回当前订阅的不可变快照。 */
    public synchronized Collection<MqttSubscription> subscriptions() {
        return List.copyOf(subscriptions.values());
    }

    /**
     * 分配 packetId，并将 QoS 1/2 消息加入出站待确认队列。
     *
     * @param message 应用消息
     * @param deliveryQos 实际投递 QoS
     * @param retained 是否设置保留标志
     */
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

    /**
     * 完成 QoS 1 消息并释放 packetId 与载荷计数。
     *
     * @param packetId 报文标识符
     */
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

    /**
     * 将 QoS 2 出站消息推进到等待 PUBCOMP。
     *
     * @param packetId 报文标识符
     */
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

    /**
     * 完成 QoS 2 出站消息并释放相关资源。
     *
     * @param packetId 报文标识符
     */
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

    /**
     * 登记入站 QoS 2 消息，并识别合法重传、协议冲突及资源超限。
     *
     * @param packetId 报文标识符
     * @param message 应用消息
     * @param retained 是否设置保留标志
     * @param duplicate 是否为重复报文
     */
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

    /**
     * 删除并返回收到 PUBREL 的入站 QoS 2 消息。
     *
     * @param packetId 报文标识符
     */
    public synchronized Optional<MqttInboundQosTwoPublish> releaseQosTwo(int packetId) {
        return Optional.ofNullable(inboundQosTwoPublishes.remove(packetId));
    }

    /** 返回全部出站待确认消息的不可变快照。 */
    public synchronized Collection<MqttPendingPublish> pendingPublishes() {
        return List.copyOf(pendingPublishes.values());
    }

    /**
     * 按 packetId 查找出站待确认消息。
     *
     * @param packetId 报文标识符
     */
    public synchronized Optional<MqttPendingPublish> pendingPublish(int packetId) {
        return Optional.ofNullable(pendingPublishes.get(packetId));
    }

    public synchronized int nextPacketId() {
        return nextPacketId;
    }

    /** 返回出站待确认消息数量。 */
    public synchronized int pendingPublishCount() {
        return pendingPublishes.size();
    }

    public synchronized long pendingPayloadBytes() {
        return pendingPayloadBytes;
    }

    /** 判断出站待确认消息数量是否仍有容量。 */
    public synchronized boolean canEnqueue() {
        return pendingPublishes.size() < maxPendingMessages;
    }

    /**
     * 将指定出站消息标记为已发送，以便重连时设置 DUP。
     *
     * @param packetId 报文标识符
     */
    public synchronized boolean markPendingPublishSent(int packetId) {
        MqttPendingPublish pending = pendingPublishes.get(packetId);
        if (pending == null || pending.sent()) {
            return false;
        }
        pendingPublishes.put(packetId, pending.asSent());
        return true;
    }

    /** 捕获用于异步持久化的不可变会话快照。 */
    public synchronized MqttSessionSnapshot snapshot() {
        return new MqttSessionSnapshot(
                clientId,
                username,
                nextPacketId,
                List.copyOf(subscriptions.values()),
                List.copyOf(pendingPublishes.values()),
                List.copyOf(inboundQosTwoPublishes.values()));
    }

    /**
     * 使用宽松默认限制从快照恢复会话。
     *
     * @param snapshot 会话快照
     */
    public static MqttSession restore(MqttSessionSnapshot snapshot) {
        return restore(snapshot, Integer.MAX_VALUE, 65535);
    }

    /**
     * 使用指定订阅和待确认消息上限恢复会话。
     *
     * @param snapshot 会话快照
     * @param maxSubscriptions 订阅数量上限
     * @param maxPendingMessages 待确认消息数量上限
     */
    public static MqttSession restore(
            MqttSessionSnapshot snapshot,
            int maxSubscriptions,
            int maxPendingMessages) {
        return restore(snapshot, maxSubscriptions, maxPendingMessages, maxPendingMessages);
    }

    /**
     * 使用完整资源限制恢复会话及 packetId 占用状态。
     *
     * @param snapshot 会话快照
     * @param maxSubscriptions 订阅数量上限
     * @param maxPendingMessages 出站待确认消息数量上限
     * @param maxInboundInflightMessages 入站 QoS 2 消息数量上限
     */
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
