package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.authentication.MqttAuthenticationRequest;
import cn.bcd.app.mqtt.server.authentication.MqttAuthenticator;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizationAction;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizationRequest;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizer;
import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.message.MqttWillMessage;
import cn.bcd.app.mqtt.server.session.MqttOutboundPublishState;
import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;

import java.util.Objects;

/** 处理 CONNECT 校验、认证授权、会话注册和重连恢复。 */
final class MqttConnectFlow {

    private final MqttBroker broker;
    private final MqttAuthenticator authenticator;
    private final MqttAuthorizer authorizer;

    /**
     * 创建连接流程处理器。
     *
     * @param broker MQTT Broker
     * @param authenticator 认证器
     * @param authorizer 授权器
     */
    MqttConnectFlow(
            MqttBroker broker,
            MqttAuthenticator authenticator,
            MqttAuthorizer authorizer) {
        this.broker = Objects.requireNonNull(broker);
        this.authenticator = Objects.requireNonNull(authenticator);
        this.authorizer = Objects.requireNonNull(authorizer);
    }

    /**
     * 校验 CONNECT 报文并异步完成认证、授权和会话注册。
     *
     * @param connection 当前连接
     * @param message CONNECT 报文
     */
    void handle(
            MqttConnection connection,
            MqttConnectMessage message) {
        // 当前服务只实现 MQTT 3.1.1；对 MQTT 5 使用协议规定的“不支持版本”返回码。
        int protocolVersion = message.variableHeader().version();
        if (protocolVersion != MqttVersion.MQTT_3_1_1.protocolLevel()) {
            connection.refuse(
                    protocolVersion == MqttVersion.MQTT_5.protocolLevel()
                            ? MqttConnectReturnCode.CONNECTION_REFUSED_UNSUPPORTED_PROTOCOL_VERSION
                            : MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION);
            return;
        }

        String clientId = message.payload().clientIdentifier();
        if (clientId == null || clientId.isEmpty()) {
            connection.refuse(
                    MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            return;
        }
        // MQTT 3.1.1 不允许只携带密码而不携带用户名。
        if (message.variableHeader().hasPassword()
                && !message.variableHeader().hasUserName()) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        // 遗嘱字段之间存在组合约束，必须在认证和注册会话前完成整体校验。
        MqttWillMessage willMessage = willMessage(message);
        if (!isValidWill(message, willMessage)) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        String username = message.payload().userName();
        // 先认证身份，再使用认证后的 clientId、username 检查遗嘱发布权限。
        if (!authenticator.authenticate(new MqttAuthenticationRequest(
                clientId, username, message.payload().passwordInBytes()))) {
            connection.refuse(
                    MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            return;
        }
        if (willMessage != null && !authorizer.authorize(new MqttAuthorizationRequest(
                MqttAuthorizationAction.PUBLISH,
                clientId,
                username,
                willMessage.message().topicName()))) {
            connection.refuse(
                    MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
            return;
        }

        // 将 CONNECT 中后续仍需使用的数据复制为只读上下文，不再持有入站报文对象。
        MqttConnectionContext connectionContext = new MqttConnectionContext(
                clientId,
                message.variableHeader().isCleanSession(),
                message.variableHeader().keepAliveTimeSeconds(),
                username,
                willMessage);
        // Broker 注册及持久化可能异步完成，CONNACK 必须等待其最终结果。
        connection.onCompletion(broker.connect(
                connection,
                connectionContext.clientId(),
                connectionContext.username(),
                connectionContext.cleanSession()), result -> {
            if (!result.accepted()) {
                connection.refuse(
                        MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
                return;
            }
            connection.accept(connectionContext, result, willMessage);
            resendPendingPublishes(connection);
        });
    }

    /**
     * 按持久化状态恢复重连客户端尚未完成的 QoS 流程。
     *
     * @param connection 已恢复会话的连接
     */
    private void resendPendingPublishes(MqttConnection connection) {
        broker.pendingPublishes(connection).forEach(pending -> {
            if (pending.state() == MqttOutboundPublishState.WAIT_PUBCOMP) {
                // QoS 2 已进入第二阶段，重连时只重发 PUBREL，不能再次投递载荷。
                connection.writeQosControlPacket(
                        MqttMessageType.PUBREL, pending.packetId());
                return;
            }
            // 只有曾成功交给连接发送的 PUBLISH 才需要设置 DUP。
            boolean duplicate = pending.sent();
            connection.onCompletion(
                    broker.markPendingPublishSent(connection, pending.packetId()),
                    ignored -> connection.sendPublish(
                            pending.message(),
                            pending.packetId(),
                            pending.retained(),
                            duplicate));
        });
    }

    /**
     * 从合法的 CONNECT 遗嘱字段构造内部遗嘱消息。
     *
     * @param message CONNECT 报文
     */
    private static MqttWillMessage willMessage(MqttConnectMessage message) {
        if (!message.variableHeader().isWillFlag()) {
            return null;
        }
        // Netty 暴露的是整数 QoS，需要显式排除保留值 3 及异常负值。
        int willQos = message.variableHeader().willQos();
        if (willQos < 0 || willQos > 2) {
            return null;
        }
        String willTopic = message.payload().willTopic();
        byte[] willPayload = message.payload().willMessageInBytes();
        // 遗嘱主题必须是主题名而不是带通配符的订阅过滤器。
        if (!MqttTopicFilter.isValidTopicName(willTopic) || willPayload == null) {
            return null;
        }
        return new MqttWillMessage(
                new MqttApplicationMessage(
                        willTopic,
                        willPayload,
                        MqttQoS.valueOf(willQos)),
                message.variableHeader().isWillRetain());
    }

    /**
     * 校验 Will Flag、Will QoS、Will Retain 与遗嘱内容的组合关系。
     *
     * @param message CONNECT 报文
     * @param willMessage 解析后的遗嘱消息
     */
    private static boolean isValidWill(
            MqttConnectMessage message,
            MqttWillMessage willMessage) {
        if (!message.variableHeader().isWillFlag()) {
            return message.variableHeader().willQos() == 0
                    && !message.variableHeader().isWillRetain();
        }
        return willMessage != null;
    }
}
