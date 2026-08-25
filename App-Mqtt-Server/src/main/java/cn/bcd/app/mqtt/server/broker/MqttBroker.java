package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.config.MqttResourceLimits;
import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.message.MqttWillMessage;
import cn.bcd.app.mqtt.server.retained.MqttRetainedMessageStore;
import cn.bcd.app.mqtt.server.session.MqttInboundPublishStatus;
import cn.bcd.app.mqtt.server.session.MqttPendingPublish;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import cn.bcd.app.mqtt.server.session.persistence.MqttSessionStore;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class MqttBroker {

    private final MqttClientRegistry clients;
    private final MqttMessageRouter router;

    public MqttBroker(
            MqttRetainedMessageStore retainedMessageStore,
            MqttSessionStore sessionStore) {
        this(retainedMessageStore, sessionStore, MqttResourceLimits.defaults());
    }

    @Autowired
    public MqttBroker(
            MqttRetainedMessageStore retainedMessageStore,
            MqttSessionStore sessionStore,
            MqttServerProperties properties) {
        this(retainedMessageStore, sessionStore, MqttResourceLimits.from(properties.getLimits()));
    }

    private MqttBroker(
            MqttRetainedMessageStore retainedMessageStore,
            MqttSessionStore sessionStore,
            MqttResourceLimits limits) {
        clients = new MqttClientRegistry(sessionStore, limits);
        router = new MqttMessageRouter(clients, retainedMessageStore);
    }

    public CompletionStage<MqttConnectResult> connect(
            MqttConnection connection,
            String clientId,
            String username,
            boolean cleanSession) {
        return clients.connect(connection, clientId, username, cleanSession)
                .thenApply(registration -> {
                    if (!registration.result().accepted()) {
                        return registration.result();
                    }
                    MqttConnection previous = registration.previousConnection();
                    if (previous != null && previous != connection) {
                        previous.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                    }
                    return registration.result();
                });
    }

    public void disconnect(MqttConnection connection) {
        clients.disconnect(connection);
    }

    public CompletionStage<MqttSubscribeResult> subscribe(
            MqttConnection connection,
            MqttSubscription subscription) {
        return clients.subscribe(connection, subscription)
                .thenApply(result -> switch (result) {
                    case ACCEPTED -> new MqttSubscribeResult(
                            true, true, router.findRetained(subscription.topicFilter()));
                    case REJECTED -> new MqttSubscribeResult(true, false, List.of());
                    case NOT_CURRENT -> new MqttSubscribeResult(false, false, List.of());
                });
    }

    public CompletionStage<Boolean> unsubscribe(
            MqttConnection connection,
            Collection<String> topicFilters) {
        return clients.unsubscribe(connection, topicFilters);
    }

    public CompletionStage<Boolean> publish(
            MqttConnection publisher,
            MqttApplicationMessage message,
            boolean retained) {
        if (!clients.isCurrent(publisher)) {
            return CompletableFuture.completedFuture(false);
        }
        return router.publish(message, retained).thenApply(ignored -> true);
    }

    public void publishWill(MqttWillMessage willMessage) {
        router.publishWill(willMessage);
    }

    public CompletionStage<Boolean> deliverRetained(
            MqttConnection connection,
            MqttApplicationMessage message) {
        return router.deliverRetained(connection, message);
    }

    public void acknowledge(MqttConnection connection, int packetId) {
        clients.acknowledge(connection, packetId);
    }

    public CompletionStage<MqttInboundPublishStatus> receiveQosTwo(
            MqttConnection connection,
            int packetId,
            MqttApplicationMessage message,
            boolean retained,
            boolean duplicate) {
        return clients.receiveQosTwo(
                connection, packetId, message, retained, duplicate);
    }

    public CompletionStage<Boolean> releaseQosTwo(
            MqttConnection connection,
            int packetId) {
        if (!clients.isCurrent(connection)) {
            return CompletableFuture.completedFuture(false);
        }
        return clients.releaseQosTwo(connection, packetId)
                .thenCompose(pending -> pending
                        .map(message -> router.publish(
                                message.message(), message.retained()))
                        .orElseGet(() -> CompletableFuture.completedFuture(null)))
                .thenApply(ignored -> true);
    }

    public CompletionStage<Optional<MqttPendingPublish>> receivePubRec(
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

    public CompletionStage<Void> markPendingPublishSent(
            MqttConnection connection,
            int packetId) {
        return clients.markPendingPublishSent(connection, packetId);
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
