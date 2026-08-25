package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class MqttClientRegistry {

    private final ConcurrentMap<String, MqttClientState> clients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ClientLock> clientLocks = new ConcurrentHashMap<>();
    private final MqttSubscriptionIndex subscriptionIndex = new MqttSubscriptionIndex();
    private final MqttSessionStore sessionStore;

    MqttClientRegistry(MqttSessionStore sessionStore) {
        this.sessionStore = Objects.requireNonNull(sessionStore);
        for (MqttSessionSnapshot snapshot : sessionStore.loadAll()) {
            MqttSession session = MqttSession.restore(snapshot);
            clients.put(session.clientId(), new MqttClientState(session, null, true));
            subscriptionIndex.add(session);
        }
    }

    MqttClientRegistration connect(
            MqttConnection connection,
            String clientId,
            String username,
            boolean cleanSession) {
        return withClientLock(clientId, () -> {
            MqttClientState current = clients.get(clientId);
            boolean sameIdentity = current != null
                    && Objects.equals(current.session().username(), username);
            boolean sessionPresent = !cleanSession
                    && current != null
                    && current.persistent()
                    && sameIdentity;
            if (!sessionPresent && current != null) {
                subscriptionIndex.remove(current.session());
            }
            if (cleanSession || current != null && !sessionPresent) {
                sessionStore.delete(clientId);
            }

            MqttSession session = sessionPresent
                    ? current.session()
                    : new MqttSession(clientId, username);
            MqttClientState registered = new MqttClientState(
                    session, connection, !cleanSession);
            clients.put(clientId, registered);
            persist(registered);
            return new MqttClientRegistration(
                    new MqttConnectResult(session, sessionPresent),
                    current == null ? null : current.connection());
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
                persist(current);
                clients.put(session.clientId(), current.offline());
            } else {
                subscriptionIndex.remove(current.session());
                clients.remove(session.clientId(), current);
            }
            return null;
        });
    }

    boolean subscribe(MqttConnection connection, MqttSubscription subscription) {
        MqttSession session = connection.session();
        return session != null && withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null) {
                return false;
            }
            boolean changed = session.subscribe(subscription);
            subscriptionIndex.add(session.clientId(), subscription);
            if (changed) {
                persist(current);
            }
            return true;
        });
    }

    boolean unsubscribe(
            MqttConnection connection,
            Collection<String> topicFilters) {
        MqttSession session = connection.session();
        return session != null && withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null) {
                return false;
            }
            boolean changed = false;
            for (String topicFilter : topicFilters) {
                changed |= session.unsubscribe(topicFilter);
                subscriptionIndex.remove(session.clientId(), topicFilter);
            }
            if (changed) {
                persist(current);
            }
            return true;
        });
    }

    boolean isCurrent(MqttConnection connection) {
        MqttSession session = connection.session();
        return session != null && current(connection, session) != null;
    }

    Set<String> findSubscribers(String topicName) {
        return subscriptionIndex.findSubscribers(topicName);
    }

    Optional<Delivery> prepareDelivery(
            String clientId,
            MqttApplicationMessage message,
            boolean retained) {
        return withClientLock(clientId, () -> prepareDelivery(
                clients.get(clientId), message, retained));
    }

    Optional<Delivery> prepareDelivery(
            MqttConnection connection,
            MqttApplicationMessage message,
            boolean retained) {
        MqttSession session = connection.session();
        if (session == null) {
            return Optional.empty();
        }
        return withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            return prepareDelivery(current, message, retained);
        });
    }

    void acknowledge(MqttConnection connection, int packetId) {
        mutateCurrent(connection, state -> {
            if (state.session().acknowledgeQosOne(packetId)) {
                persist(state);
            }
        });
    }

    MqttInboundPublishStatus receiveQosTwo(
            MqttConnection connection,
            int packetId,
            MqttApplicationMessage message,
            boolean retained,
            boolean duplicate) {
        MqttSession session = connection.session();
        if (session == null) {
            return MqttInboundPublishStatus.PROTOCOL_ERROR;
        }
        return withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null) {
                return MqttInboundPublishStatus.PROTOCOL_ERROR;
            }
            MqttInboundPublishStatus status = session.receiveQosTwo(
                    packetId, message, retained, duplicate);
            if (status == MqttInboundPublishStatus.STORED) {
                persist(current);
            }
            return status;
        });
    }

    Optional<MqttInboundQosTwoPublish> releaseQosTwo(
            MqttConnection connection,
            int packetId) {
        MqttSession session = connection.session();
        if (session == null) {
            return Optional.empty();
        }
        return withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null) {
                return Optional.empty();
            }
            Optional<MqttInboundQosTwoPublish> pending = session.releaseQosTwo(packetId);
            if (pending.isPresent()) {
                persist(current);
            }
            return pending;
        });
    }

    Optional<MqttPendingPublish> receivePubRec(
            MqttConnection connection,
            int packetId) {
        MqttSession session = connection.session();
        if (session == null) {
            return Optional.empty();
        }
        return withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current == null) {
                return Optional.empty();
            }
            Optional<MqttPendingPublish> pending = session.receivePubRec(packetId);
            if (pending.isPresent()) {
                persist(current);
            }
            return pending;
        });
    }

    void receivePubComp(MqttConnection connection, int packetId) {
        mutateCurrent(connection, state -> {
            if (state.session().receivePubComp(packetId)) {
                persist(state);
            }
        });
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

    void markPendingPublishSent(MqttConnection connection, int packetId) {
        mutateCurrent(connection, state -> {
            if (state.session().markPendingPublishSent(packetId)) {
                persist(state);
            }
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

    private Optional<Delivery> prepareDelivery(
            MqttClientState state,
            MqttApplicationMessage message,
            boolean retained) {
        if (state == null) {
            return Optional.empty();
        }
        Optional<MqttQoS> subscriptionQos = state.session()
                .maximumQosMatching(message.topicName());
        if (subscriptionQos.isEmpty()) {
            return Optional.empty();
        }
        MqttQoS deliveryQos = MqttQoS.valueOf(
                Math.min(message.qos().value(), subscriptionQos.orElseThrow().value()));
        if (deliveryQos == MqttQoS.AT_MOST_ONCE) {
            return state.connection() == null
                    ? Optional.empty()
                    : Optional.of(new Delivery(
                            state.connection(), message.withQos(deliveryQos), 0, retained));
        }

        MqttPendingPublish pending = state.session().enqueue(
                message, deliveryQos, retained);
        if (state.connection() != null) {
            state.session().markPendingPublishSent(pending.packetId());
        }
        persist(state);
        return state.connection() == null
                ? Optional.empty()
                : Optional.of(new Delivery(
                        state.connection(),
                        pending.message(),
                        pending.packetId(),
                        pending.retained()));
    }

    private void mutateCurrent(
            MqttConnection connection,
            Consumer<MqttClientState> mutation) {
        MqttSession session = connection.session();
        if (session == null) {
            return;
        }
        withClientLock(session.clientId(), () -> {
            MqttClientState current = current(connection, session);
            if (current != null) {
                mutation.accept(current);
            }
            return null;
        });
    }

    private MqttClientState current(
            MqttConnection connection,
            MqttSession session) {
        MqttClientState state = clients.get(session.clientId());
        return state != null && state.connection() == connection ? state : null;
    }

    private void persist(MqttClientState state) {
        if (state.persistent()) {
            sessionStore.save(state.session().snapshot());
        }
    }

    private <T> T withClientLock(String clientId, Supplier<T> action) {
        ClientLock clientLock = clientLocks.compute(clientId, (key, current) -> {
            ClientLock lock = current == null ? new ClientLock() : current;
            lock.references++;
            return lock;
        });
        clientLock.lock.lock();
        try {
            return action.get();
        } finally {
            clientLock.lock.unlock();
            clientLocks.computeIfPresent(clientId, (key, current) -> {
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
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

    private record MqttClientState(
            MqttSession session,
            MqttConnection connection,
            boolean persistent
    ) {
        MqttClientState offline() {
            return new MqttClientState(session, null, true);
        }
    }

    private static final class ClientLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
