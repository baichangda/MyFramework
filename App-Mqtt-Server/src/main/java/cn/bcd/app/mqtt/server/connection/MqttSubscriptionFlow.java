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
        subscribeNext(connection, requests, 0, subAck, retainedMessages);
    }

    private void subscribeNext(
            MqttConnection connection,
            List<MqttTopicSubscription> requests,
            int index,
            MqttMessageBuilders.SubAckBuilder subAck,
            Map<String, MqttApplicationMessage> retainedMessages) {
        if (index == requests.size()) {
            connection.write(subAck.build());
            deliverRetained(connection, retainedMessages.values());
            return;
        }
        MqttTopicSubscription request = requests.get(index);
        String topicFilter = request.topicFilter();
        MqttQoS requestedQos = request.qualityOfService();
        if (!authorize(connection, MqttAuthorizationAction.SUBSCRIBE, topicFilter)) {
            subAck.addGrantedQos(MqttQoS.FAILURE);
            subscribeNext(connection, requests, index + 1, subAck, retainedMessages);
            return;
        }
        connection.onCompletion(broker.subscribe(
                connection, new MqttSubscription(topicFilter, requestedQos)), result -> {
            if (!result.subscribed()) {
                connection.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                return;
            }
            if (!result.granted()) {
                subAck.addGrantedQos(MqttQoS.FAILURE);
                subscribeNext(connection, requests, index + 1, subAck, retainedMessages);
                return;
            }
            result.retainedMessages().forEach(
                    retained -> retainedMessages.put(retained.topicName(), retained));
            subAck.addGrantedQos(requestedQos);
            subscribeNext(connection, requests, index + 1, subAck, retainedMessages);
        });
    }

    private void deliverRetained(
            MqttConnection connection,
            Iterable<MqttApplicationMessage> retainedMessages) {
        for (MqttApplicationMessage retained : retainedMessages) {
            connection.onCompletion(
                    broker.deliverRetained(connection, retained),
                    delivered -> {
                        if (!delivered) {
                            connection.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                        }
                    });
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
        connection.onCompletion(
                broker.unsubscribe(connection, topicFilters),
                unsubscribed -> {
                    if (!unsubscribed) {
                        connection.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                        return;
                    }
                    connection.write(MqttMessageBuilders.unsubAck()
                            .packetId(packetId)
                            .build());
                });
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
