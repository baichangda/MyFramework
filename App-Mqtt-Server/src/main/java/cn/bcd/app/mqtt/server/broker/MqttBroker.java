package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.message.MqttWillMessage;
import cn.bcd.app.mqtt.server.retained.MqttRetainedMessageStore;
import cn.bcd.app.mqtt.server.session.MqttPendingPublish;
import cn.bcd.app.mqtt.server.session.MqttInboundPublishStatus;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MqttBroker {

    private final ConcurrentMap<String, MqttClientState> clients = new ConcurrentHashMap<>();
    private final MqttSubscriptionIndex subscriptionIndex = new MqttSubscriptionIndex();
    private final MqttRetainedMessageStore retainedMessageStore;

    public MqttBroker(MqttRetainedMessageStore retainedMessageStore) {
        this.retainedMessageStore = Objects.requireNonNull(retainedMessageStore);
    }

    public MqttConnectResult connect(
            MqttConnection connection,
            String clientId,
            String username,
            boolean cleanSession) {
        AtomicReference<MqttConnection> previousConnection = new AtomicReference<>();
        AtomicReference<MqttConnectResult> resultReference = new AtomicReference<>();

        clients.compute(clientId, (key, current) -> {
            boolean sameIdentity = current != null
                    && Objects.equals(current.session().username(), username);
            boolean sessionPresent = !cleanSession && current != null
                    && current.persistent() && sameIdentity;
            if (!sessionPresent && current != null) {
                subscriptionIndex.remove(current.session());
            }
            MqttSession session = sessionPresent
                    ? current.session()
                    : new MqttSession(clientId, username);
            previousConnection.set(current == null ? null : current.connection());
            resultReference.set(new MqttConnectResult(session, sessionPresent));
            return new MqttClientState(session, connection, !cleanSession);
        });

        MqttConnection previous = previousConnection.get();
        if (previous != null && previous != connection) {
            previous.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
        }
        return resultReference.get();
    }

    public void disconnect(MqttConnection connection) {
        MqttSession session = connection.session();
        if (session == null) {
            return;
        }
        clients.computeIfPresent(session.clientId(), (key, current) -> {
            if (current.connection() != connection) {
                return current;
            }
            if (current.persistent()) {
                return new MqttClientState(current.session(), null, true);
            }
            subscriptionIndex.remove(current.session());
            return null;
        });
    }

    public MqttSubscribeResult subscribe(
            MqttConnection connection,
            MqttSubscription subscription) {
        MqttSession session = connection.session();
        if (session == null) {
            return new MqttSubscribeResult(false, List.of());
        }
        AtomicBoolean subscribed = new AtomicBoolean();
        clients.computeIfPresent(session.clientId(), (key, current) -> {
            if (current.connection() == connection) {
                current.session().subscribe(subscription);
                subscriptionIndex.add(session.clientId(), subscription);
                subscribed.set(true);
            }
            return current;
        });
        return subscribed.get()
                ? new MqttSubscribeResult(
                        true, retainedMessageStore.findMatching(subscription.topicFilter()))
                : new MqttSubscribeResult(false, List.of());
    }

    public boolean unsubscribe(
            MqttConnection connection,
            Collection<String> topicFilters) {
        MqttSession session = connection.session();
        if (session == null) {
            return false;
        }
        AtomicBoolean unsubscribed = new AtomicBoolean();
        clients.computeIfPresent(session.clientId(), (key, current) -> {
            if (current.connection() == connection) {
                for (String topicFilter : topicFilters) {
                    current.session().unsubscribe(topicFilter);
                    subscriptionIndex.remove(session.clientId(), topicFilter);
                }
                unsubscribed.set(true);
            }
            return current;
        });
        return unsubscribed.get();
    }

    public boolean publish(
            MqttConnection publisher,
            MqttApplicationMessage message,
            boolean retained) {
        MqttSession publisherSession = publisher.session();
        if (publisherSession == null) {
            return false;
        }
        MqttClientState publisherState = clients.get(publisherSession.clientId());
        if (publisherState == null || publisherState.connection() != publisher) {
            return false;
        }

        publish(message, retained);
        return true;
    }

    public void publishWill(MqttWillMessage willMessage) {
        publish(willMessage.message(), willMessage.retained());
    }

    private void publish(
            MqttApplicationMessage message,
            boolean retained) {
        if (retained) {
            if (message.isEmpty()) {
                retainedMessageStore.delete(message.topicName());
            } else {
                retainedMessageStore.save(message);
            }
        }

        for (String clientId : subscriptionIndex.findSubscribers(message.topicName())) {
            MqttClientState state = clients.get(clientId);
            if (state == null) {
                continue;
            }
            state.session().maximumQosMatching(message.topicName())
                    .ifPresent(subscriptionQos -> deliver(
                            state, message, subscriptionQos, false));
        }
    }

    public boolean deliverRetained(
            MqttConnection connection,
            MqttApplicationMessage message) {
        MqttSession session = connection.session();
        if (session == null) {
            return false;
        }
        MqttClientState state = clients.get(session.clientId());
        if (state == null || state.connection() != connection) {
            return false;
        }
        session.maximumQosMatching(message.topicName())
                .ifPresent(subscriptionQos -> deliver(
                        state, message, subscriptionQos, true));
        return true;
    }

    public void acknowledge(MqttConnection connection, int packetId) {
        MqttSession session = connection.session();
        if (session == null) {
            return;
        }
        MqttClientState state = clients.get(session.clientId());
        if (state != null && state.connection() == connection) {
            session.acknowledgeQosOne(packetId);
        }
    }

    public MqttInboundPublishStatus receiveQosTwo(
            MqttConnection connection,
            int packetId,
            MqttApplicationMessage message,
            boolean retained,
            boolean duplicate) {
        MqttSession session = connection.session();
        if (!isCurrentConnection(connection, session)) {
            return MqttInboundPublishStatus.PROTOCOL_ERROR;
        }
        return session.receiveQosTwo(packetId, message, retained, duplicate);
    }

    public boolean releaseQosTwo(MqttConnection connection, int packetId) {
        MqttSession session = connection.session();
        if (!isCurrentConnection(connection, session)) {
            return false;
        }
        session.releaseQosTwo(packetId).ifPresent(pending -> publish(
                connection, pending.message(), pending.retained()));
        return true;
    }

    public Optional<MqttPendingPublish> receivePubRec(
            MqttConnection connection,
            int packetId) {
        MqttSession session = connection.session();
        return isCurrentConnection(connection, session)
                ? session.receivePubRec(packetId)
                : Optional.empty();
    }

    public void receivePubComp(MqttConnection connection, int packetId) {
        MqttSession session = connection.session();
        if (isCurrentConnection(connection, session)) {
            session.receivePubComp(packetId);
        }
    }

    public Collection<MqttPendingPublish> pendingPublishes(
            MqttConnection connection) {
        MqttSession session = connection.session();
        if (session == null) {
            return List.of();
        }
        MqttClientState state = clients.get(session.clientId());
        return state != null && state.connection() == connection
                ? session.pendingPublishes()
                : List.of();
    }

    private void deliver(
            MqttClientState state,
            MqttApplicationMessage message,
            MqttQoS subscriptionQos,
            boolean retained) {
        MqttQoS deliveryQos = MqttQoS.valueOf(
                Math.min(message.qos().value(), subscriptionQos.value()));
        MqttConnection subscriber = state.connection();
        if (deliveryQos == MqttQoS.AT_MOST_ONCE) {
            if (subscriber != null) {
                subscriber.sendPublish(message.withQos(deliveryQos), 0, retained, false);
            }
            return;
        }
        MqttPendingPublish pending = state.session().enqueue(message, deliveryQos, retained);
        if (subscriber != null) {
            pending.markSent();
            subscriber.sendPublish(
                    pending.message(), pending.packetId(), pending.retained(), false);
        }
    }

    private boolean isCurrentConnection(
            MqttConnection connection,
            MqttSession session) {
        if (session == null) {
            return false;
        }
        MqttClientState state = clients.get(session.clientId());
        return state != null && state.connection() == connection;
    }

    public Optional<MqttConnection> findConnection(String clientId) {
        MqttClientState state = clients.get(clientId);
        return state == null || state.connection() == null
                ? Optional.empty()
                : Optional.of(state.connection());
    }

    public Optional<MqttSession> findSession(String clientId) {
        MqttClientState state = clients.get(clientId);
        return state == null ? Optional.empty() : Optional.of(state.session());
    }

    public int size() {
        return clients.size();
    }

    private record MqttClientState(
            MqttSession session,
            MqttConnection connection,
            boolean persistent
    ) {
    }
}
