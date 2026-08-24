package cn.bcd.app.mqtt.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.springframework.stereotype.Component;

@Component
public class MqttPingHandler {

    public void handle(ChannelHandlerContext context) {
        context.writeAndFlush(MqttMessage.PINGRESP);
    }
}
