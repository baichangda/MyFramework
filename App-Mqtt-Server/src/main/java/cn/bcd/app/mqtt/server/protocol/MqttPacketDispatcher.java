package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.connection.MqttConnectionAttributes;
import cn.bcd.app.mqtt.server.handler.MqttConnectHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class MqttPacketDispatcher extends SimpleChannelInboundHandler<MqttMessage> {

    private final MqttConnectHandler connectHandler;

    public MqttPacketDispatcher(MqttConnectHandler connectHandler) {
        this.connectHandler = connectHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, MqttMessage message) {
        if (message.decoderResult().isFailure()) {
            context.close();
            return;
        }

        boolean connected = context.channel()
                .attr(MqttConnectionAttributes.CONNECTION_CONTEXT)
                .get() != null;
        if (!connected && message.fixedHeader().messageType() == MqttMessageType.CONNECT) {
            connectHandler.handle(context, (MqttConnectMessage) message);
            return;
        }

        context.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        context.close();
    }
}
