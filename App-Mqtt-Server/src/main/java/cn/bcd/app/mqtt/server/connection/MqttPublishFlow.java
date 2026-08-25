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

/** 处理客户端发布消息以及 QoS 1/2 双向确认状态流转。 */
final class MqttPublishFlow {

    private final MqttBroker broker;
    private final MqttAuthorizer authorizer;

    /**
     * 创建发布流程处理器。
     *
     * @param broker MQTT Broker
     * @param authorizer 授权器
     */
    MqttPublishFlow(MqttBroker broker, MqttAuthorizer authorizer) {
        this.broker = Objects.requireNonNull(broker);
        this.authorizer = Objects.requireNonNull(authorizer);
    }

    /**
     * 校验并处理客户端发送的 PUBLISH 报文。
     *
     * @param connection 当前连接
     * @param message PUBLISH 报文
     */
    void publish(
            MqttConnection connection,
            MqttPublishMessage message) {
        MqttQoS qos = message.fixedHeader().qosLevel();
        // QoS 0 不存在确认与重传流程，因此协议禁止设置 DUP。
        if (qos == MqttQoS.AT_MOST_ONCE && message.fixedHeader().isDup()) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        int packetId = message.variableHeader().packetId();
        // QoS 1/2 依赖 packetId 关联确认状态，0 是协议保留值。
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
            // MQTT 3.1.1 的 PUBLISH 没有可表达“未授权”的否定确认，直接关闭连接。
            connection.close(MqttConnectionCloseReason.AUTHORIZATION_FAILED);
            return;
        }

        // 复制 ByteBuf 内容，使后续异步路由不依赖 Netty 对入站缓冲区的释放时机。
        MqttApplicationMessage applicationMessage = new MqttApplicationMessage(
                topicName, ByteBufUtil.getBytes(message.payload()), qos);
        if (qos == MqttQoS.EXACTLY_ONCE) {
            // QoS 2 第一阶段仅保存消息；收到 PUBREL 后才真正路由，保证恰好一次处理。
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
        // QoS 0/1 立即路由；QoS 1 只有在路由及持久化完成后才返回 PUBACK。
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

    /**
     * 处理客户端对 Broker 出站 QoS 1 消息的 PUBACK。
     *
     * @param connection 当前连接
     * @param message PUBACK 报文
     */
    void pubAck(MqttConnection connection, MqttPubAckMessage message) {
        int packetId = message.variableHeader().messageId();
        if (packetId == 0) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        broker.acknowledge(connection, packetId);
    }

    /**
     * 处理出站 QoS 2 第一阶段确认 PUBREC。
     *
     * @param connection 当前连接
     * @param message PUBREC 报文
     */
    void pubRec(MqttConnection connection, MqttMessage message) {
        int packetId = packetId(message);
        // 将出站 QoS 2 消息推进到等待 PUBCOMP，并发送第二阶段的 PUBREL。
        connection.onCompletion(
                broker.receivePubRec(connection, packetId),
                pending -> pending.ifPresent(value -> connection.writeQosControlPacket(
                        MqttMessageType.PUBREL, value.packetId())));
    }

    /**
     * 处理入站 QoS 2 第二阶段请求 PUBREL。
     *
     * @param connection 当前连接
     * @param message PUBREL 报文
     */
    void pubRel(MqttConnection connection, MqttMessage message) {
        int packetId = packetId(message);
        // 原子释放已保存的入站消息后再发布；重复 PUBREL 不会重复路由载荷。
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

    /**
     * 处理出站 QoS 2 最终确认 PUBCOMP。
     *
     * @param connection 当前连接
     * @param message PUBCOMP 报文
     */
    void pubComp(MqttConnection connection, MqttMessage message) {
        broker.receivePubComp(connection, packetId(message));
    }

    /**
     * 校验当前连接是否拥有目标主题的发布权限。
     *
     * @param connection 当前连接
     * @param topic 主题名
     */
    private boolean authorize(MqttConnection connection, String topic) {
        MqttConnectionContext context = connection.context();
        return context != null && authorizer.authorize(new MqttAuthorizationRequest(
                MqttAuthorizationAction.PUBLISH,
                context.clientId(),
                context.username(),
                topic));
    }

    /**
     * 从带报文标识符的控制报文中提取 packetId。
     *
     * @param message MQTT 控制报文
     */
    private static int packetId(MqttMessage message) {
        return ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
    }
}
