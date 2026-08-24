package cn.bcd.app.mqtt.server.handler;

import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionContext;
import cn.bcd.app.mqtt.server.connection.MqttConnectionRegistry;
import cn.bcd.app.mqtt.server.connection.MqttKeepAliveManager;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.springframework.stereotype.Component;

@Component
public class MqttConnectHandler {

    private final MqttKeepAliveManager keepAliveManager;
    private final MqttConnectionRegistry connectionRegistry;

    public MqttConnectHandler(
            MqttKeepAliveManager keepAliveManager,
            MqttConnectionRegistry connectionRegistry) {
        this.keepAliveManager = keepAliveManager;
        this.connectionRegistry = connectionRegistry;
    }

    public void handle(
            ChannelHandlerContext nettyContext,
            MqttConnection connection,
            MqttConnectMessage message) {
        int protocolVersion = message.variableHeader().version();
        if (protocolVersion != MqttVersion.MQTT_3_1_1.protocolLevel()) {
            refuse(nettyContext, connection, protocolVersion == MqttVersion.MQTT_5.protocolLevel()
                    ? MqttConnectReturnCode.CONNECTION_REFUSED_UNSUPPORTED_PROTOCOL_VERSION
                    : MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION);
            return;
        }

        String clientId = message.payload().clientIdentifier();
        if (clientId == null || clientId.isEmpty()) {
            refuse(nettyContext, connection, MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            return;
        }

        MqttConnectionContext mqttContext = new MqttConnectionContext(
                clientId,
                message.variableHeader().isCleanSession(),
                message.variableHeader().keepAliveTimeSeconds(),
                message.payload().userName());
        connection.establish(mqttContext);
        keepAliveManager.configure(nettyContext, mqttContext.keepAliveSeconds());
        connectionRegistry.register(mqttContext.clientId(), connection);
        nettyContext.writeAndFlush(MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(false)
                .build());
    }

    private void refuse(
            ChannelHandlerContext nettyContext,
            MqttConnection connection,
            MqttConnectReturnCode returnCode) {
        connection.recordCloseReason(MqttConnectionCloseReason.CONNECTION_REFUSED);
        nettyContext.writeAndFlush(MqttMessageBuilders.connAck()
                        .returnCode(returnCode)
                        .sessionPresent(false)
                        .build())
                .addListener(ChannelFutureListener.CLOSE);
    }
}
