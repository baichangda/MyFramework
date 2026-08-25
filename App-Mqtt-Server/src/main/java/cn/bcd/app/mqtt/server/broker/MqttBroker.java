package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.message.MqttWillMessage;
import cn.bcd.app.mqtt.server.retained.MqttRetainedMessageStore;
import cn.bcd.app.mqtt.server.session.MqttInboundPublishStatus;
import cn.bcd.app.mqtt.server.session.MqttInboundQosTwoPublish;
import cn.bcd.app.mqtt.server.session.MqttPendingPublish;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import cn.bcd.app.mqtt.server.session.persistence.MqttSessionStore;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class MqttBroker {

    private final MqttClientRegistry clients;
    private final MqttMessageRouter router;

    public MqttBroker(
            MqttRetainedMessageStore retainedMessageStore,
            MqttSessionStore sessionStore) {
        clients = new MqttClientRegistry(sessionStore);
        router = new MqttMessageRouter(clients, retainedMessageStore);
    }

    public MqttConnectResult connect(
            MqttConnection connection,
            String clientId,
            String username,
            boolean cleanSession) {
        MqttClientRegistry.MqttClientRegistration registration = clients.connect(
                connection, clientId, username, cleanSession);
        MqttConnection previous = registration.previousConnection();
        if (previous != null && previous != connection) {
            previous.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
        }
        return registration.result();
    }

    public void disconnect(MqttConnection connection) {
        clients.disconnect(connection);
    }

    public MqttSubscribeResult subscribe(
            MqttConnection connection,
            MqttSubscription subscription) {
        if (!clients.subscribe(connection, subscription)) {
            return new MqttSubscribeResult(false, List.of());
        }
        return new MqttSubscribeResult(
                true, router.findRetained(subscription.topicFilter()));
    }

    public boolean unsubscribe(
            MqttConnection connection,
            Collection<String> topicFilters) {
        return clients.unsubscribe(connection, topicFilters);
    }

    public boolean publish(
            MqttConnection publisher,
            MqttApplicationMessage message,
            boolean retained) {
        if (!clients.isCurrent(publisher)) {
            return false;
        }
        router.publish(message, retained);
        return true;
    }

    public void publishWill(MqttWillMessage willMessage) {
        router.publishWill(willMessage);
    }

    public boolean deliverRetained(
            MqttConnection connection,
            MqttApplicationMessage message) {
        return router.deliverRetained(connection, message);
    }

    public void acknowledge(MqttConnection connection, int packetId) {
        clients.acknowledge(connection, packetId);
    }

    public MqttInboundPublishStatus receiveQosTwo(
            MqttConnection connection,
            int packetId,
            MqttApplicationMessage message,
            boolean retained,
            boolean duplicate) {
        return clients.receiveQosTwo(
                connection, packetId, message, retained, duplicate);
    }

    public boolean releaseQosTwo(MqttConnection connection, int packetId) {
        if (!clients.isCurrent(connection)) {
            return false;
        }
        Optional<MqttInboundQosTwoPublish> pending = clients.releaseQosTwo(
                connection, packetId);
        pending.ifPresent(message -> router.publish(
                message.message(), message.retained()));
        return true;
    }

    public Optional<MqttPendingPublish> receivePubRec(
            MqttConnection connection,
            int packetId) {
        return clients.receivePubRec(connection, packetId);
    }

    public void receivePubComp(MqttConnection connection, int packetId) {
        clients.receivePubComp(connection, packetId);
    }

    public Collection<MqttPendingPublish> pendingPublishes(
            MqttConnection connection) {
        return clients.pendingPublishes(connection);
    }

    public void markPendingPublishSent(
            MqttConnection connection,
            int packetId) {
        clients.markPendingPublishSent(connection, packetId);
    }

    public Optional<MqttConnection> findConnection(String clientId) {
        return clients.findConnection(clientId);
    }

    public Optional<MqttSession> findSession(String clientId) {
        return clients.findSession(clientId);
    }

    public int size() {
        return clients.size();
    }
}
