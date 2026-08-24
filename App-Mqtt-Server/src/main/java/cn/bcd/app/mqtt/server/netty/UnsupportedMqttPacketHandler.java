package cn.bcd.app.mqtt.server.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttMessage;

@ChannelHandler.Sharable
final class UnsupportedMqttPacketHandler extends SimpleChannelInboundHandler<MqttMessage> {

    static final UnsupportedMqttPacketHandler INSTANCE = new UnsupportedMqttPacketHandler();

    private UnsupportedMqttPacketHandler() {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, MqttMessage message) {
        context.close();
    }
}
