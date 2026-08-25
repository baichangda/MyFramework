package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.authorization.MqttAuthorizationAction;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizationRequest;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizer;
import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.broker.MqttSubscribeResult;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MqttSubscriptionFlow {

    private final MqttBroker broker;
    private final MqttAuthorizer authorizer;

    MqttSubscriptionFlow(MqttBroker broker, MqttAuthorizer authorizer) {
        this.broker = Objects.requireNonNull(broker);
        this.authorizer = Objects.requireNonNull(authorizer);
    }

    void subscribe(
            MqttConnection connection,
            MqttSubscribeMessage message) {
        int packetId = message.variableHeader().messageId();
        List<MqttTopicSubscription> requests = message.payload().topicSubscriptions();
        if (packetId == 0 || requests.isEmpty()
                || message.fixedHeader().qosLevel() != MqttQoS.AT_LEAST_ONCE
                || message.fixedHeader().isRetain()) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        for (MqttTopicSubscription request : requests) {
            if (!MqttTopicFilter.isValid(request.topicFilter())
                    || !isSubscriptionQos(request.qualityOfService())) {
                connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
                return;
            }
        }

        MqttMessageBuilders.SubAckBuilder subAck = MqttMessageBuilders.subAck()
                .packetId(packetId);
        Map<String, MqttApplicationMessage> retainedMessages = new LinkedHashMap<>();
        for (MqttTopicSubscription request : requests) {
            String topicFilter = request.topicFilter();
            MqttQoS requestedQos = request.qualityOfService();
            if (!authorize(connection, MqttAuthorizationAction.SUBSCRIBE, topicFilter)) {
                subAck.addGrantedQos(MqttQoS.FAILURE);
                continue;
            }
            MqttSubscribeResult result = broker.subscribe(
                    connection, new MqttSubscription(topicFilter, requestedQos));
            if (!result.subscribed()) {
                connection.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                return;
            }
            result.retainedMessages().forEach(
                    retained -> retainedMessages.put(retained.topicName(), retained));
            subAck.addGrantedQos(requestedQos);
        }
        connection.write(subAck.build());
        for (MqttApplicationMessage retained : retainedMessages.values()) {
            if (!broker.deliverRetained(connection, retained)) {
                connection.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                return;
            }
        }
    }

    void unsubscribe(
            MqttConnection connection,
            MqttUnsubscribeMessage message) {
        int packetId = message.variableHeader().messageId();
        List<String> topicFilters = message.payload().topics();
        if (packetId == 0 || topicFilters.isEmpty()
                || message.fixedHeader().qosLevel() != MqttQoS.AT_LEAST_ONCE
                || message.fixedHeader().isRetain()
                || topicFilters.stream().anyMatch(
                        topicFilter -> !MqttTopicFilter.isValid(topicFilter))) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        if (!broker.unsubscribe(connection, topicFilters)) {
            connection.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
            return;
        }
        connection.write(MqttMessageBuilders.unsubAck()
                .packetId(packetId)
                .build());
    }

    private boolean authorize(
            MqttConnection connection,
            MqttAuthorizationAction action,
            String topic) {
        MqttConnectionContext context = connection.context();
        return context != null && authorizer.authorize(new MqttAuthorizationRequest(
                action, context.clientId(), context.username(), topic));
    }

    private static boolean isSubscriptionQos(MqttQoS qos) {
        return qos == MqttQoS.AT_MOST_ONCE
                || qos == MqttQoS.AT_LEAST_ONCE
                || qos == MqttQoS.EXACTLY_ONCE;
    }
}
