package cn.bcd.app.mqtt.server.netty;

import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.springframework.stereotype.Component;

@Component
public class MqttChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MqttServerProperties properties;

    public MqttChannelInitializer(MqttServerProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        channel.pipeline().addLast("mqttDecoder", new MqttDecoder(
                properties.getMaxPacketSize(),
                properties.getMaxClientIdLength(),
                true));
        channel.pipeline().addLast("mqttEncoder", MqttEncoder.INSTANCE);
        channel.pipeline().addLast("mqttPacketSink", UnsupportedMqttPacketHandler.INSTANCE);
    }
}
