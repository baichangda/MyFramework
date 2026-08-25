package cn.bcd.app.mqtt.server.support;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;

import java.nio.charset.StandardCharsets;

public final class MqttTestChannel {

    private MqttTestChannel() {
    }

    public static EmbeddedChannel open() {
        return open(MqttTestBroker.create());
    }

    public static EmbeddedChannel open(MqttBroker broker) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(new MqttConnection(broker));
        return channel;
    }

    public static EmbeddedChannel connect(
            MqttBroker broker,
            String clientId,
            boolean cleanSession) {
        EmbeddedChannel channel = open(broker);
        channel.writeInbound(Unpooled.wrappedBuffer(
                connectPacket(clientId, cleanSession)));
        readOutbound(channel);
        return channel;
    }

    public static byte[] connectPacket(String clientId, boolean cleanSession) {
        byte[] clientIdBytes = clientId.getBytes(StandardCharsets.UTF_8);
        ByteBuf packet = Unpooled.buffer(14 + clientIdBytes.length);
        packet.writeByte(0x10);
        packet.writeByte(12 + clientIdBytes.length);
        packet.writeShort(4).writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
        packet.writeByte(4);
        packet.writeByte(cleanSession ? 0x02 : 0x00);
        packet.writeShort(60);
        packet.writeShort(clientIdBytes.length).writeBytes(clientIdBytes);
        return readAndRelease(packet);
    }

    public static MqttConnection connection(EmbeddedChannel channel) {
        return channel.pipeline().get(MqttConnection.class);
    }

    public static byte[] readOutbound(EmbeddedChannel channel) {
        return readAndRelease(channel.readOutbound());
    }

    private static byte[] readAndRelease(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }
}
