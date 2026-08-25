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

/** 处理 SUBSCRIBE、UNSUBSCRIBE 及首次订阅时的保留消息投递。 */
final class MqttSubscriptionFlow {

    private final MqttBroker broker;
    private final MqttAuthorizer authorizer;

    /**
     * 创建订阅流程处理器。
     *
     * @param broker MQTT Broker
     * @param authorizer 授权器
     */
    MqttSubscriptionFlow(MqttBroker broker, MqttAuthorizer authorizer) {
        this.broker = Objects.requireNonNull(broker);
        this.authorizer = Objects.requireNonNull(authorizer);
    }

    /**
     * 校验并处理一个可能包含多条主题过滤器的 SUBSCRIBE 报文。
     *
     * @param connection 当前连接
     * @param message SUBSCRIBE 报文
     */
    void subscribe(
            MqttConnection connection,
            MqttSubscribeMessage message) {
        int packetId = message.variableHeader().messageId();
        List<MqttTopicSubscription> requests = message.payload().topicSubscriptions();
        // SUBSCRIBE 固定头必须使用 QoS 1，且请求列表不能为空。
        if (packetId == 0 || requests.isEmpty()
                || message.fixedHeader().qosLevel() != MqttQoS.AT_LEAST_ONCE
                || message.fixedHeader().isRetain()) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        // 先校验整批请求，避免前半批已经生效后才发现后续过滤器非法。
        for (MqttTopicSubscription request : requests) {
            if (!MqttTopicFilter.isValid(request.topicFilter())
                    || !isSubscriptionQos(request.qualityOfService())) {
                connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
                return;
            }
        }

        MqttMessageBuilders.SubAckBuilder subAck = MqttMessageBuilders.subAck()
                .packetId(packetId);
        // 多个过滤器可能命中同一保留主题，按主题名去重后只投递一次。
        Map<String, MqttApplicationMessage> retainedMessages = new LinkedHashMap<>();
        subscribeNext(connection, requests, 0, subAck, retainedMessages);
    }

    /**
     * 按请求顺序异步处理下一条订阅并累计 SUBACK 返回码。
     *
     * @param connection 当前连接
     * @param requests 订阅请求列表
     * @param index 当前处理位置
     * @param subAck SUBACK 构造器
     * @param retainedMessages 已收集的保留消息
     */
    private void subscribeNext(
            MqttConnection connection,
            List<MqttTopicSubscription> requests,
            int index,
            MqttMessageBuilders.SubAckBuilder subAck,
            Map<String, MqttApplicationMessage> retainedMessages) {
        if (index == requests.size()) {
            // 先发送完整 SUBACK，再异步投递本批订阅匹配到的保留消息。
            connection.write(subAck.build());
            deliverRetained(connection, retainedMessages.values());
            return;
        }
        MqttTopicSubscription request = requests.get(index);
        String topicFilter = request.topicFilter();
        MqttQoS requestedQos = request.qualityOfService();
        if (!authorize(connection, MqttAuthorizationAction.SUBSCRIBE, topicFilter)) {
            // 单条订阅未授权通过 SUBACK FAILURE 表达，不影响同一报文中的其他过滤器。
            subAck.addGrantedQos(MqttQoS.FAILURE);
            subscribeNext(connection, requests, index + 1, subAck, retainedMessages);
            return;
        }
        // 串行处理各过滤器，确保 SUBACK 返回码顺序与请求顺序严格一致。
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
            // 后处理请求覆盖先处理请求时，以同主题的最后一个对象去重，消息内容本身一致。
            result.retainedMessages().forEach(
                    retained -> retainedMessages.put(retained.topicName(), retained));
            subAck.addGrantedQos(requestedQos);
            subscribeNext(connection, requests, index + 1, subAck, retainedMessages);
        });
    }

    /**
     * 投递本批新订阅匹配且已按主题去重的保留消息。
     *
     * @param connection 目标连接
     * @param retainedMessages 保留消息集合
     */
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

    /**
     * 校验并处理批量取消订阅请求。
     *
     * @param connection 当前连接
     * @param message UNSUBSCRIBE 报文
     */
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

    /**
     * 使用连接身份校验指定主题操作。
     *
     * @param connection 当前连接
     * @param action 操作类型
     * @param topic 主题名或过滤器
     */
    private boolean authorize(
            MqttConnection connection,
            MqttAuthorizationAction action,
            String topic) {
        MqttConnectionContext context = connection.context();
        return context != null && authorizer.authorize(new MqttAuthorizationRequest(
                action, context.clientId(), context.username(), topic));
    }

    /**
     * 判断 QoS 是否可用于订阅请求。
     *
     * @param qos 服务质量等级
     */
    private static boolean isSubscriptionQos(MqttQoS qos) {
        return qos == MqttQoS.AT_MOST_ONCE
                || qos == MqttQoS.AT_LEAST_ONCE
                || qos == MqttQoS.EXACTLY_ONCE;
    }
}
