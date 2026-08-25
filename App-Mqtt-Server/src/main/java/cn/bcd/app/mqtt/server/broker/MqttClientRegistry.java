package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.config.MqttResourceLimits;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.session.MqttInboundPublishStatus;
import cn.bcd.app.mqtt.server.session.MqttInboundQosTwoPublish;
import cn.bcd.app.mqtt.server.session.MqttPendingPublish;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSessionSnapshot;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import cn.bcd.app.mqtt.server.session.persistence.MqttSessionStore;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 管理 clientId、活动连接及持久会话之间的一致关系。
 *
 * <p>不同 clientId 可并行操作；同一 clientId 的连接接管、订阅变更、消息排队和确认
 * 通过独立监视器串行化，避免全局锁限制吞吐量。</p>
 */
final class MqttClientRegistry {

    private final ConcurrentMap<String, MqttClientState> clients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ClientMonitor> clientMonitors =
            new ConcurrentHashMap<>();
    private final MqttSubscriptionIndex subscriptionIndex = new MqttSubscriptionIndex();
    private final MqttSessionStore sessionStore;
    private final MqttResourceLimits limits;
    private final AtomicInteger registeredClientIds = new AtomicInteger();

    /**
     * 使用默认资源上限创建客户端注册表。
     *
     * @param sessionStore 会话存储
     */
    MqttClientRegistry(MqttSessionStore sessionStore) {
        this(sessionStore, MqttResourceLimits.defaults());
    }

    /**
     * 加载持久会话并使用指定资源上限创建客户端注册表。
     *
     * @param sessionStore 会话存储
     * @param limits 资源上限
     */
    MqttClientRegistry(MqttSessionStore sessionStore, MqttResourceLimits limits) {
        this.sessionStore = Objects.requireNonNull(sessionStore);
        this.limits = Objects.requireNonNull(limits);
        // 启动时先恢复持久会话及订阅索引，之后才接受新的网络连接。
        for (MqttSessionSnapshot snapshot : sessionStore.loadAll()) {
            MqttSession session = MqttSession.restore(
                    snapshot,
                    limits.subscriptionsPerSession(),
                    Math.max(limits.inflightMessagesPerSession(),
                            limits.offlineMessagesPerSession()),
                    limits.inflightMessagesPerSession());
            clients.put(session.clientId(), new MqttClientState(session, null, true));
            subscriptionIndex.add(session);
        }
        registeredClientIds.set(clients.size());
    }

    /**
     * 串行注册 clientId 对应的新连接，并决定是否恢复旧会话。
     *
     * @param connection 新连接
     * @param clientId 客户端标识
     * @param username 用户名
     * @param cleanSession 是否清理旧会话
     */
    CompletionStage<MqttClientRegistration> connect(
            MqttConnection connection,
            String clientId,
            String username,
            boolean cleanSession) {
        return withClientLock(clientId, () -> {
            MqttClientState current = clients.get(clientId);
            // 只有全新 clientId 消耗配额；重连或接管复用已有登记项。
            if (current == null && !reserveClientId()) {
                return completed(new MqttClientRegistration(
                        MqttConnectResult.rejected(), null));
            }
            // 持久会话只允许由相同用户名恢复，避免更换身份后继承旧订阅和离线消息。
            boolean sameIdentity = current != null
                    && Objects.equals(current.session().username(), username);
            // 恢复会话必须同时满足非清理连接、持久会话存在以及身份相同。
            boolean sessionPresent = !cleanSession
                    && current != null
                    && current.persistent()
                    && sameIdentity;
            CompletionStage<Void> persistence = completed();
            if (!sessionPresent && current != null) {
                subscriptionIndex.remove(current.session());
            }
            // cleanSession 或身份变化都需要清除旧会话及其级联持久化数据。
            if (cleanSession || current != null && !sessionPresent) {
                persistence = sessionStore.deleteSession(clientId);
            }

            MqttSession session = sessionPresent
                    ? current.session()
                    : new MqttSession(
                            clientId,
                            username,
                            limits.subscriptionsPerSession(),
                            Math.max(limits.inflightMessagesPerSession(),
                                    limits.offlineMessagesPerSession()),
                            limits.inflightMessagesPerSession());
            MqttClientState registered = new MqttClientState(
                    session, connection, !cleanSession);
            clients.put(clientId, registered);
            if (registered.persistent()) {
                persistence = persistence.thenCompose(ignored -> sessionStore.upsertSession(
                        session.clientId(), session.username(), session.nextPacketId()));
            }
            MqttClientRegistration registration = new MqttClientRegistration(
                    MqttConnectResult.accepted(session, sessionPresent),
                    current == null ? null : current.connection());
            return persistence.thenApply(ignored -> registration);
        });
    }

    /**
     * 注销当前连接，并按持久性保留离线会话或删除临时会话。
     *
     * @param connection 待注销连接
     */
    void disconnect(MqttConnection connection) {
        MqttSession session = connection.session();
        if (session == null) {
            return;
        }
        withClientLock(session.clientId(), () -> {
            MqttClientState current = clients.get(session.clientId());
            // 被接管的旧连接稍后断开时不能清理新连接已接管的会话。
            if (current == null || current.connection() != connection) {
                return null;
            }
            if (current.persistent()) {
                clients.put(session.clientId(), current.offline());
            } else {
                subscriptionIndex.remove(current.session());
                if (clients.remove(session.clientId(), current)) {
                    registeredClientIds.decrementAndGet();
                }
            }
            return null;
        });
    }

    /**
     * 为当前连接新增或更新订阅。
     *
     * @param connection 当前连接
     * @param subscription 订阅内容
     */
    CompletionStage<SubscriptionResult> subscribe(
            MqttConnection connection,
            MqttSubscription subscription) {
        MqttSession session = connection.session();
        if (session == null) {
            return completed(SubscriptionResult.NOT_CURRENT);
        }
        return withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null) {
                return completed(SubscriptionResult.NOT_CURRENT);
            }
            // 先检查容量再同时修改会话和索引，避免两者出现部分更新。
            if (!session.canSubscribe(subscription.topicFilter())) {
                return completed(SubscriptionResult.REJECTED);
            }
            boolean changed = session.subscribe(subscription);
            subscriptionIndex.add(session.clientId(), subscription);
            CompletionStage<Void> persistence = changed && current.persistent()
                    ? sessionStore.upsertSubscription(session.clientId(), subscription)
                    : completed();
            return persistence.thenApply(ignored -> SubscriptionResult.ACCEPTED);
        });
    }

    /**
     * 批量删除当前连接的订阅。
     *
     * @param connection 当前连接
     * @param topicFilters 主题过滤器集合
     */
    CompletionStage<Boolean> unsubscribe(
            MqttConnection connection,
            Collection<String> topicFilters) {
        MqttSession session = connection.session();
        if (session == null) {
            return completed(false);
        }
        return withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null) {
                return completed(false);
            }
            boolean changed = false;
            for (String topicFilter : topicFilters) {
                changed |= session.unsubscribe(topicFilter);
                subscriptionIndex.remove(session.clientId(), topicFilter);
            }
            CompletionStage<Void> persistence = changed && current.persistent()
                    ? sessionStore.deleteSubscriptions(session.clientId(), topicFilters)
                    : completed();
            return persistence.thenApply(ignored -> true);
        });
    }

    /**
     * 判断连接是否仍是 clientId 当前登记的连接。
     *
     * @param connection 待检查连接
     */
    boolean isCurrent(MqttConnection connection) {
        MqttSession session = connection.session();
        return session != null && current(connection, session) != null;
    }

    /**
     * 查找主题匹配的订阅客户端及最高 QoS。
     *
     * @param topicName 主题名
     */
    Map<String, MqttQoS> findSubscribers(String topicName) {
        return subscriptionIndex.findSubscribers(topicName);
    }

    /**
     * 按 clientId 为普通路由消息准备投递。
     *
     * @param clientId 客户端标识
     * @param message 应用消息
     * @param subscriptionQos 订阅 QoS
     * @param retained 是否设置保留标志
     */
    CompletionStage<Optional<Delivery>> prepareDelivery(
            String clientId,
            MqttApplicationMessage message,
            MqttQoS subscriptionQos,
            boolean retained) {
        return withClientLock(clientId, () -> prepareDelivery(
                clients.get(clientId), message, subscriptionQos, retained));
    }

    /**
     * 为刚完成订阅的当前连接准备保留消息投递。
     *
     * @param connection 当前连接
     * @param message 保留消息
     * @param retained 是否设置保留标志
     */
    CompletionStage<Optional<Delivery>> prepareDelivery(
            MqttConnection connection,
            MqttApplicationMessage message,
            boolean retained) {
        MqttSession session = connection.session();
        if (session == null) {
            return completed(Optional.empty());
        }
        MqttQoS subscriptionQos = subscriptionIndex
                .findSubscribers(message.topicName())
                .get(session.clientId());
        if (subscriptionQos == null) {
            return completed(Optional.empty());
        }
        return withClientLock(session.clientId(), () -> prepareDelivery(
                current(connection, session), message, subscriptionQos, retained));
    }

    /**
     * 完成出站 QoS 1 消息并异步删除持久化记录。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     */
    void acknowledge(MqttConnection connection, int packetId) {
        MqttSession session = connection.session();
        if (session == null) {
            return;
        }
        CompletionStage<Void> persistence = withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null || !session.acknowledgeQosOne(packetId)) {
                return completed();
            }
            return current.persistent()
                    ? sessionStore.deletePendingPublish(session.clientId(), packetId)
                    : completed();
        });
        closeOnFailure(connection, persistence);
    }

    /**
     * 登记入站 QoS 2 消息，并在持久会话中保存状态。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     * @param message 应用消息
     * @param retained 是否设置保留标志
     * @param duplicate 是否为重复报文
     */
    CompletionStage<MqttInboundPublishStatus> receiveQosTwo(
            MqttConnection connection,
            int packetId,
            MqttApplicationMessage message,
            boolean retained,
            boolean duplicate) {
        MqttSession session = connection.session();
        if (session == null) {
            return completed(MqttInboundPublishStatus.PROTOCOL_ERROR);
        }
        return withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null) {
                return completed(MqttInboundPublishStatus.PROTOCOL_ERROR);
            }
            MqttInboundPublishStatus status = session.receiveQosTwo(
                    packetId, message, retained, duplicate);
            if (status != MqttInboundPublishStatus.STORED || !current.persistent()) {
                return completed(status);
            }
            MqttInboundQosTwoPublish inbound = new MqttInboundQosTwoPublish(
                    packetId, message, retained);
            return sessionStore.upsertInboundQosTwo(session.clientId(), inbound)
                    .thenApply(ignored -> status);
        });
    }

    /**
     * 释放入站 QoS 2 消息并删除持久化记录。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     */
    CompletionStage<Optional<MqttInboundQosTwoPublish>> releaseQosTwo(
            MqttConnection connection,
            int packetId) {
        MqttSession session = connection.session();
        if (session == null) {
            return completed(Optional.empty());
        }
        return withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null) {
                return completed(Optional.empty());
            }
            Optional<MqttInboundQosTwoPublish> pending = session.releaseQosTwo(packetId);
            CompletionStage<Void> persistence = pending.isPresent() && current.persistent()
                    ? sessionStore.deleteInboundQosTwo(session.clientId(), packetId)
                    : completed();
            return persistence.thenApply(ignored -> pending);
        });
    }

    /**
     * 推进出站 QoS 2 状态并持久化 WAIT_PUBCOMP。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     */
    CompletionStage<Optional<MqttPendingPublish>> receivePubRec(
            MqttConnection connection,
            int packetId) {
        MqttSession session = connection.session();
        if (session == null) {
            return completed(Optional.empty());
        }
        return withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null) {
                return completed(Optional.empty());
            }
            Optional<MqttPendingPublish> pending = session.receivePubRec(packetId);
            CompletionStage<Void> persistence = pending.isPresent() && current.persistent()
                    ? sessionStore.upsertPendingPublish(
                            session.clientId(), session.nextPacketId(), pending.orElseThrow())
                    : completed();
            return persistence.thenApply(ignored -> pending);
        });
    }

    /**
     * 完成出站 QoS 2 消息并删除持久化记录。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     */
    void receivePubComp(MqttConnection connection, int packetId) {
        MqttSession session = connection.session();
        if (session == null) {
            return;
        }
        CompletionStage<Void> persistence = withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null || !session.receivePubComp(packetId)) {
                return completed();
            }
            return current.persistent()
                    ? sessionStore.deletePendingPublish(session.clientId(), packetId)
                    : completed();
        });
        closeOnFailure(connection, persistence);
    }

    /**
     * 返回当前连接会话的待确认消息快照。
     *
     * @param connection 当前连接
     */
    Collection<MqttPendingPublish> pendingPublishes(MqttConnection connection) {
        MqttSession session = connection.session();
        if (session == null) {
            return List.of();
        }
        return withClientLock(session.clientId(), () -> current(connection, session) == null
                ? List.of()
                : session.pendingPublishes());
    }

    /**
     * 标记消息已经发送，并持久化该重传状态。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     */
    CompletionStage<Void> markPendingPublishSent(
            MqttConnection connection,
            int packetId) {
        MqttSession session = connection.session();
        if (session == null) {
            return completed();
        }
        return withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null || !session.markPendingPublishSent(packetId)) {
                return completed();
            }
            return current.persistent()
                    ? sessionStore.upsertPendingPublish(
                            session.clientId(),
                            session.nextPacketId(),
                            session.pendingPublish(packetId).orElseThrow())
                    : completed();
        });
    }

    /**
     * 按 clientId 查找活动连接。
     *
     * @param clientId 客户端标识
     */
    Optional<MqttConnection> findConnection(String clientId) {
        MqttClientState state = clients.get(clientId);
        return state == null || state.connection() == null
                ? Optional.empty()
                : Optional.of(state.connection());
    }

    /**
     * 按 clientId 查找会话。
     *
     * @param clientId 客户端标识
     */
    Optional<MqttSession> findSession(String clientId) {
        MqttClientState state = clients.get(clientId);
        return state == null ? Optional.empty() : Optional.of(state.session());
    }

    /** 返回已登记客户端数量。 */
    int size() {
        return clients.size();
    }

    /** 以 CAS 方式预留一个客户端标识配额。 */
    private boolean reserveClientId() {
        int current = registeredClientIds.get();
        while (current < limits.clientIds()) {
            if (registeredClientIds.compareAndSet(current, current + 1)) {
                return true;
            }
            current = registeredClientIds.get();
        }
        return false;
    }

    /**
     * 在已锁定 clientId 的前提下计算 QoS、检查资源并持久化投递。
     *
     * @param state 客户端状态
     * @param message 应用消息
     * @param subscriptionQos 订阅 QoS
     * @param retained 是否设置保留标志
     */
    private CompletionStage<Optional<Delivery>> prepareDelivery(
            MqttClientState state,
            MqttApplicationMessage message,
            MqttQoS subscriptionQos,
            boolean retained) {
        if (state == null) {
            return completed(Optional.empty());
        }
        // 实际投递 QoS 取发布 QoS 与订阅 QoS 的较低值。
        MqttQoS deliveryQos = MqttQoS.valueOf(
                Math.min(message.qos().value(), subscriptionQos.value()));
        // QoS 0 不进入待确认队列；离线时无法重传，只能直接丢弃。
        if (deliveryQos == MqttQoS.AT_MOST_ONCE) {
            return completed(state.connection() == null
                    ? Optional.empty()
                    : Optional.of(new Delivery(
                            state.connection(), message.withQos(deliveryQos), 0, retained)));
        }

        boolean offline = state.connection() == null;
        // 在线连接受飞行窗口约束；离线会话同时受消息数和载荷字节数约束。
        boolean limitExceeded = offline
                ? state.session().pendingPublishCount() >= limits.offlineMessagesPerSession()
                        || state.session().pendingPayloadBytes() + message.payloadLength()
                                > limits.offlineQueueBytesPerSession()
                : state.session().pendingPublishCount()
                        >= limits.inflightMessagesPerSession();
        // 在线超限会破坏继续投递的可靠性，因此断开；离线超限仅拒绝继续排队。
        if (limitExceeded) {
            if (!offline) {
                state.connection().close(MqttConnectionCloseReason.RESOURCE_LIMIT_EXCEEDED);
            }
            return completed(Optional.empty());
        }

        // QoS 1/2 必须先进入会话并完成持久化，之后才允许写入活动连接。
        MqttPendingPublish pending = state.session().enqueue(
                message, deliveryQos, retained);
        // 在线消息将在持久化完成后立即发送，预先标记 sent 供断线重连设置 DUP。
        if (state.connection() != null) {
            state.session().markPendingPublishSent(pending.packetId());
            pending = state.session().pendingPublish(pending.packetId()).orElseThrow();
        }
        CompletionStage<Void> persistence = state.persistent()
                ? sessionStore.upsertPendingPublish(
                        state.session().clientId(), state.session().nextPacketId(), pending)
                : completed();
        MqttPendingPublish persisted = pending;
        return persistence.thenApply(ignored -> state.connection() == null
                ? Optional.empty()
                : Optional.of(new Delivery(
                        state.connection(),
                        persisted.message(),
                        persisted.packetId(),
                        persisted.retained())));
    }

    /**
     * 校验连接和会话是否仍对应当前登记状态。
     *
     * @param connection 待校验连接
     * @param session 待校验会话
     */
    private MqttClientState current(
            MqttConnection connection,
            MqttSession session) {
        MqttClientState state = clients.get(session.clientId());
        return state != null && state.connection() == connection ? state : null;
    }

    /**
     * 使用 clientId 独立监视器串行执行操作。
     *
     * @param clientId 客户端标识
     * @param action 待执行操作
     * @param <T> 操作结果类型
     */
    private <T> T withClientLock(String clientId, Supplier<T> action) {
        // 引用计数防止锁对象在仍有线程等待时从 Map 中移除并被重新创建。
        ClientMonitor monitor = clientMonitors.compute(clientId, (key, current) -> {
            ClientMonitor value = current == null ? new ClientMonitor() : current;
            value.references++;
            return value;
        });
        try {
            synchronized (monitor) {
                // 监视器覆盖包含异步阶段创建在内的全部内存状态变更。
                return action.get();
            }
        } finally {
            // 最后一个使用者离开后移除监视器，避免 clientId 无界增长造成锁对象泄漏。
            clientMonitors.computeIfPresent(clientId, (key, current) -> {
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
    }

    /** 返回已经完成的空异步阶段。 */
    private static CompletionStage<Void> completed() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 返回携带指定值的已完成异步阶段。
     *
     * @param value 结果值
     * @param <T> 结果类型
     */
    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    /**
     * 持久化失败时以内错原因关闭连接。
     *
     * @param connection 当前连接
     * @param persistence 持久化阶段
     */
    private static void closeOnFailure(
            MqttConnection connection,
            CompletionStage<Void> persistence) {
        persistence.exceptionally(exception -> {
            connection.close(MqttConnectionCloseReason.INTERNAL_ERROR);
            return null;
        });
    }

    record MqttClientRegistration(
            MqttConnectResult result,
            MqttConnection previousConnection
    ) {
    }

    record Delivery(
            MqttConnection connection,
            MqttApplicationMessage message,
            int packetId,
            boolean retained
    ) {
    }

    enum SubscriptionResult {
        ACCEPTED,
        REJECTED,
        NOT_CURRENT
    }

    private record MqttClientState(
            MqttSession session,
            MqttConnection connection,
            boolean persistent
    ) {
        /** 返回保留会话但清除活动连接的离线状态。 */
        MqttClientState offline() {
            return new MqttClientState(session, null, true);
        }
    }

    private static final class ClientMonitor {
        private int references;
    }
}
