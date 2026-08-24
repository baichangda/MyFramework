package cn.bcd.app.mqtt.server.netty;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.springframework.stereotype.Component;

@Component
public class MqttChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MqttServerProperties properties;
    private final MqttBroker broker;

    public MqttChannelInitializer(MqttServerProperties properties, MqttBroker broker) {
        this.properties = properties;
        this.broker = broker;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        channel.pipeline().addLast("mqttDecoder", new MqttDecoder(
                properties.getMaxPacketSize(),
                properties.getMaxClientIdLength(),
                true));
        channel.pipeline().addLast("mqttEncoder", MqttEncoder.INSTANCE);
        channel.pipeline().addLast("mqttConnection", new MqttConnection(broker));
    }
}
