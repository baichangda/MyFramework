package cn.bcd.app.mqtt.server.netty;

import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionRegistry;
import cn.bcd.app.mqtt.server.handler.MqttConnectHandler;
import cn.bcd.app.mqtt.server.handler.MqttDisconnectHandler;
import cn.bcd.app.mqtt.server.handler.MqttPingHandler;
import cn.bcd.app.mqtt.server.protocol.MqttPacketDispatcher;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.springframework.stereotype.Component;

@Component
public class MqttChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MqttServerProperties properties;
    private final MqttConnectionRegistry connectionRegistry;
    private final MqttConnectHandler connectHandler;
    private final MqttPingHandler pingHandler;
    private final MqttDisconnectHandler disconnectHandler;

    public MqttChannelInitializer(
            MqttServerProperties properties,
            MqttConnectionRegistry connectionRegistry,
            MqttConnectHandler connectHandler,
            MqttPingHandler pingHandler,
            MqttDisconnectHandler disconnectHandler) {
        this.properties = properties;
        this.connectionRegistry = connectionRegistry;
        this.connectHandler = connectHandler;
        this.pingHandler = pingHandler;
        this.disconnectHandler = disconnectHandler;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        MqttConnection connection = new MqttConnection(channel);
        channel.pipeline().addLast("mqttDecoder", new MqttDecoder(
                properties.getMaxPacketSize(),
                properties.getMaxClientIdLength(),
                true));
        channel.pipeline().addLast("mqttEncoder", MqttEncoder.INSTANCE);
        channel.pipeline().addLast("mqttPacketDispatcher", new MqttPacketDispatcher(
                connection,
                connectionRegistry,
                connectHandler,
                pingHandler,
                disconnectHandler));
    }
}
