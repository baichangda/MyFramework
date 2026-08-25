package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.authorization.MqttAuthorizationAction;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizationRequest;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizer;
import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.session.MqttInboundPublishStatus;
import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Objects;

final class MqttPublishFlow {

    private final MqttBroker broker;
    private final MqttAuthorizer authorizer;

    MqttPublishFlow(MqttBroker broker, MqttAuthorizer authorizer) {
        this.broker = Objects.requireNonNull(broker);
        this.authorizer = Objects.requireNonNull(authorizer);
    }

    void publish(
            MqttConnection connection,
            MqttPublishMessage message) {
        MqttQoS qos = message.fixedHeader().qosLevel();
        if (qos == MqttQoS.AT_MOST_ONCE && message.fixedHeader().isDup()) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        int packetId = message.variableHeader().packetId();
        if (qos != MqttQoS.AT_MOST_ONCE && packetId == 0) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        String topicName = message.variableHeader().topicName();
        if (!MqttTopicFilter.isValidTopicName(topicName)) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        if (!authorize(connection, topicName)) {
            connection.close(MqttConnectionCloseReason.AUTHORIZATION_FAILED);
            return;
        }

        MqttApplicationMessage applicationMessage = new MqttApplicationMessage(
                topicName, ByteBufUtil.getBytes(message.payload()), qos);
        if (qos == MqttQoS.EXACTLY_ONCE) {
            connection.onCompletion(broker.receiveQosTwo(
                    connection,
                    packetId,
                    applicationMessage,
                    message.fixedHeader().isRetain(),
                    message.fixedHeader().isDup()), status -> {
                if (status == MqttInboundPublishStatus.PROTOCOL_ERROR) {
                    connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
                    return;
                }
                if (status == MqttInboundPublishStatus.RESOURCE_LIMIT_EXCEEDED) {
                    connection.close(MqttConnectionCloseReason.RESOURCE_LIMIT_EXCEEDED);
                    return;
                }
                connection.writeQosControlPacket(MqttMessageType.PUBREC, packetId);
            });
            return;
        }
        connection.onCompletion(broker.publish(
                connection,
                applicationMessage,
                message.fixedHeader().isRetain()), published -> {
            if (!published) {
                connection.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                return;
            }
            if (qos == MqttQoS.AT_LEAST_ONCE) {
                connection.write(MqttMessageBuilders.pubAck()
                        .packetId(packetId)
                        .build());
            }
        });
    }

    void pubAck(MqttConnection connection, MqttPubAckMessage message) {
        int packetId = message.variableHeader().messageId();
        if (packetId == 0) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        broker.acknowledge(connection, packetId);
    }

    void pubRec(MqttConnection connection, MqttMessage message) {
        int packetId = packetId(message);
        connection.onCompletion(
                broker.receivePubRec(connection, packetId),
                pending -> pending.ifPresent(value -> connection.writeQosControlPacket(
                        MqttMessageType.PUBREL, value.packetId())));
    }

    void pubRel(MqttConnection connection, MqttMessage message) {
        int packetId = packetId(message);
        connection.onCompletion(
                broker.releaseQosTwo(connection, packetId),
                released -> {
                    if (!released) {
                        connection.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                        return;
                    }
                    connection.writeQosControlPacket(MqttMessageType.PUBCOMP, packetId);
                });
    }

    void pubComp(MqttConnection connection, MqttMessage message) {
        broker.receivePubComp(connection, packetId(message));
    }

    private boolean authorize(MqttConnection connection, String topic) {
        MqttConnectionContext context = connection.context();
        return context != null && authorizer.authorize(new MqttAuthorizationRequest(
                MqttAuthorizationAction.PUBLISH,
                context.clientId(),
                context.username(),
                topic));
    }

    private static int packetId(MqttMessage message) {
        return ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
    }
}
