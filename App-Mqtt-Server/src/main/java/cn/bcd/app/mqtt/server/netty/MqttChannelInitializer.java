package cn.bcd.app.mqtt.server.netty;

import cn.bcd.app.mqtt.server.authentication.MqttAuthenticator;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizer;
import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.springframework.stereotype.Component;

/** 为每条 TCP 连接安装 MQTT 编解码器和连接状态处理器。 */
@Component
public class MqttChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MqttServerProperties properties;
    private final MqttBroker broker;
    private final MqttAuthenticator authenticator;
    private final MqttAuthorizer authorizer;

    public MqttChannelInitializer(
            MqttServerProperties properties,
            MqttBroker broker,
            MqttAuthenticator authenticator,
            MqttAuthorizer authorizer) {
        this.properties = properties;
        this.broker = broker;
        this.authenticator = authenticator;
        this.authorizer = authorizer;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        // 解码器在协议对象进入业务层前统一限制报文大小和 clientId 长度。
        channel.pipeline().addLast("mqttDecoder", new MqttDecoder(
                properties.getMaxPacketSize(),
                properties.getMaxClientIdLength(),
                true));
        channel.pipeline().addLast("mqttEncoder", MqttEncoder.INSTANCE);
        channel.pipeline().addLast(
                "mqttConnection", new MqttConnection(
                        broker, authenticator, authorizer));
    }
}
