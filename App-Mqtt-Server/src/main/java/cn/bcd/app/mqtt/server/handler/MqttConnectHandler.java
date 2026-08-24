package cn.bcd.app.mqtt.server.handler;

import cn.bcd.app.mqtt.server.connection.MqttConnectionAttributes;
import cn.bcd.app.mqtt.server.connection.MqttConnectionContext;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.springframework.stereotype.Component;

@Component
public class MqttConnectHandler {

    public void handle(ChannelHandlerContext context, MqttConnectMessage message) {
        int protocolVersion = message.variableHeader().version();
        if (protocolVersion != MqttVersion.MQTT_3_1_1.protocolLevel()) {
            refuse(context, protocolVersion == MqttVersion.MQTT_5.protocolLevel()
                    ? MqttConnectReturnCode.CONNECTION_REFUSED_UNSUPPORTED_PROTOCOL_VERSION
                    : MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION);
            return;
        }

        String clientId = message.payload().clientIdentifier();
        if (clientId == null || clientId.isEmpty()) {
            refuse(context, MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            return;
        }

        MqttConnectionContext connectionContext = new MqttConnectionContext(
                clientId,
                message.variableHeader().isCleanSession(),
                message.variableHeader().keepAliveTimeSeconds(),
                message.payload().userName());
        context.channel().attr(MqttConnectionAttributes.CONNECTION_CONTEXT).set(connectionContext);
        context.writeAndFlush(MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(false)
                .build());
    }

    private void refuse(ChannelHandlerContext context, MqttConnectReturnCode returnCode) {
        context.writeAndFlush(MqttMessageBuilders.connAck()
                        .returnCode(returnCode)
                        .sessionPresent(false)
                        .build())
                .addListener(ChannelFutureListener.CLOSE);
    }
}
