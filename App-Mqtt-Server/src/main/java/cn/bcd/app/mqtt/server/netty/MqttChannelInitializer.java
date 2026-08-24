package cn.bcd.app.mqtt.server.netty;

import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.protocol.MqttPacketDispatcher;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.springframework.stereotype.Component;

@Component
public class MqttChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MqttServerProperties properties;
    private final MqttPacketDispatcher packetDispatcher;

    public MqttChannelInitializer(MqttServerProperties properties, MqttPacketDispatcher packetDispatcher) {
        this.properties = properties;
        this.packetDispatcher = packetDispatcher;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        channel.pipeline().addLast("mqttDecoder", new MqttDecoder(
                properties.getMaxPacketSize(),
                properties.getMaxClientIdLength(),
                true));
        channel.pipeline().addLast("mqttEncoder", MqttEncoder.INSTANCE);
        channel.pipeline().addLast("mqttPacketDispatcher", packetDispatcher);
    }
}
