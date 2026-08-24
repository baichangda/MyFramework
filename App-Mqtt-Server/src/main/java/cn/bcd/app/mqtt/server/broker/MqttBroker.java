package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.retained.MqttRetainedMessageStore;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import org.springframework.stereotype.Component;

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
            boolean cleanSession) {
        AtomicReference<MqttConnection> previousConnection = new AtomicReference<>();
        AtomicReference<MqttConnectResult> resultReference = new AtomicReference<>();

        clients.compute(clientId, (key, current) -> {
            boolean sessionPresent = !cleanSession && current != null && current.persistent();
            if (!sessionPresent && current != null) {
                subscriptionIndex.remove(current.session());
            }
            MqttSession session = sessionPresent ? current.session() : new MqttSession(clientId);
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

        if (retained) {
            if (message.isEmpty()) {
                retainedMessageStore.delete(message.topicName());
            } else {
                retainedMessageStore.save(message);
            }
        }

        for (String clientId : subscriptionIndex.findSubscribers(message.topicName())) {
            MqttClientState state = clients.get(clientId);
            if (state == null || !state.session().hasSubscriptionMatching(message.topicName())) {
                continue;
            }
            MqttConnection subscriber = state.connection();
            if (subscriber != null) {
                subscriber.sendPublish(message, false);
            }
        }
        return true;
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
