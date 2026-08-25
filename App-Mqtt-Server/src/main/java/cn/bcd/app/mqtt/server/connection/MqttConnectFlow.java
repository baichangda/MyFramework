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

final class MqttConnectFlow {

    private final MqttBroker broker;
    private final MqttAuthenticator authenticator;
    private final MqttAuthorizer authorizer;

    MqttConnectFlow(
            MqttBroker broker,
            MqttAuthenticator authenticator,
            MqttAuthorizer authorizer) {
        this.broker = Objects.requireNonNull(broker);
        this.authenticator = Objects.requireNonNull(authenticator);
        this.authorizer = Objects.requireNonNull(authorizer);
    }

    void handle(
            MqttConnection connection,
            MqttConnectMessage message) {
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
        if (message.variableHeader().hasPassword()
                && !message.variableHeader().hasUserName()) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        MqttWillMessage willMessage = willMessage(message);
        if (!isValidWill(message, willMessage)) {
            connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        String username = message.payload().userName();
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

        MqttConnectionContext connectionContext = new MqttConnectionContext(
                clientId,
                message.variableHeader().isCleanSession(),
                message.variableHeader().keepAliveTimeSeconds(),
                username,
                willMessage);
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

    private void resendPendingPublishes(MqttConnection connection) {
        broker.pendingPublishes(connection).forEach(pending -> {
            if (pending.state() == MqttOutboundPublishState.WAIT_PUBCOMP) {
                connection.writeQosControlPacket(
                        MqttMessageType.PUBREL, pending.packetId());
                return;
            }
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

    private static MqttWillMessage willMessage(MqttConnectMessage message) {
        if (!message.variableHeader().isWillFlag()) {
            return null;
        }
        int willQos = message.variableHeader().willQos();
        if (willQos < 0 || willQos > 2) {
            return null;
        }
        String willTopic = message.payload().willTopic();
        byte[] willPayload = message.payload().willMessageInBytes();
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
