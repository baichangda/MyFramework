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

    MqttClientRegistry(MqttSessionStore sessionStore) {
        this(sessionStore, MqttResourceLimits.defaults());
    }

    MqttClientRegistry(MqttSessionStore sessionStore, MqttResourceLimits limits) {
        this.sessionStore = Objects.requireNonNull(sessionStore);
        this.limits = Objects.requireNonNull(limits);
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

    CompletionStage<MqttClientRegistration> connect(
            MqttConnection connection,
            String clientId,
            String username,
            boolean cleanSession) {
        return withClientLock(clientId, () -> {
            MqttClientState current = clients.get(clientId);
            if (current == null && !reserveClientId()) {
                return completed(new MqttClientRegistration(
                        MqttConnectResult.rejected(), null));
            }
            // 持久会话只允许由相同用户名恢复，避免更换身份后继承旧订阅和离线消息。
            boolean sameIdentity = current != null
                    && Objects.equals(current.session().username(), username);
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

    void disconnect(MqttConnection connection) {
        MqttSession session = connection.session();
        if (session == null) {
            return;
        }
        withClientLock(session.clientId(), () -> {
            MqttClientState current = clients.get(session.clientId());
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

    boolean isCurrent(MqttConnection connection) {
        MqttSession session = connection.session();
        return session != null && current(connection, session) != null;
    }

    Map<String, MqttQoS> findSubscribers(String topicName) {
        return subscriptionIndex.findSubscribers(topicName);
    }

    CompletionStage<Optional<Delivery>> prepareDelivery(
            String clientId,
            MqttApplicationMessage message,
            MqttQoS subscriptionQos,
            boolean retained) {
        return withClientLock(clientId, () -> prepareDelivery(
                clients.get(clientId), message, subscriptionQos, retained));
    }

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

    Collection<MqttPendingPublish> pendingPublishes(MqttConnection connection) {
        MqttSession session = connection.session();
        if (session == null) {
            return List.of();
        }
        return withClientLock(session.clientId(), () -> current(connection, session) == null
                ? List.of()
                : session.pendingPublishes());
    }

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

    Optional<MqttConnection> findConnection(String clientId) {
        MqttClientState state = clients.get(clientId);
        return state == null || state.connection() == null
                ? Optional.empty()
                : Optional.of(state.connection());
    }

    Optional<MqttSession> findSession(String clientId) {
        MqttClientState state = clients.get(clientId);
        return state == null ? Optional.empty() : Optional.of(state.session());
    }

    int size() {
        return clients.size();
    }

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
        if (limitExceeded) {
            if (!offline) {
                state.connection().close(MqttConnectionCloseReason.RESOURCE_LIMIT_EXCEEDED);
            }
            return completed(Optional.empty());
        }

        // QoS 1/2 必须先进入会话并完成持久化，之后才允许写入活动连接。
        MqttPendingPublish pending = state.session().enqueue(
                message, deliveryQos, retained);
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

    private MqttClientState current(
            MqttConnection connection,
            MqttSession session) {
        MqttClientState state = clients.get(session.clientId());
        return state != null && state.connection() == connection ? state : null;
    }

    private <T> T withClientLock(String clientId, Supplier<T> action) {
        // 引用计数防止锁对象在仍有线程等待时从 Map 中移除并被重新创建。
        ClientMonitor monitor = clientMonitors.compute(clientId, (key, current) -> {
            ClientMonitor value = current == null ? new ClientMonitor() : current;
            value.references++;
            return value;
        });
        try {
            synchronized (monitor) {
                return action.get();
            }
        } finally {
            clientMonitors.computeIfPresent(clientId, (key, current) -> {
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
    }

    private static CompletionStage<Void> completed() {
        return CompletableFuture.completedFuture(null);
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

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
        MqttClientState offline() {
            return new MqttClientState(session, null, true);
        }
    }

    private static final class ClientMonitor {
        private int references;
    }
}
