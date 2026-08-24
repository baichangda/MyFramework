package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttPublishTest {

    @Test
    void shouldRouteQosZeroPublishToExactTopicSubscriber() {
        MqttBroker broker = new MqttBroker();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp");
        byte[] publishPacket = publishPacket(0x30, "sensor/temp", "21");

        publisher.writeInbound(Unpooled.wrappedBuffer(publishPacket));

        assertTrue(publisher.outboundMessages().isEmpty());
        assertArrayEquals(publishPacket, readOutbound(subscriber));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldRouteToAllMatchingOnlineSubscribersOnly() {
        MqttBroker broker = new MqttBroker();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel first = connectedChannel(broker, "first", true);
        EmbeddedChannel second = connectedChannel(broker, "second", true);
        EmbeddedChannel unmatched = connectedChannel(broker, "unmatched", true);
        subscribe(first, "sensor/temp");
        subscribe(second, "sensor/temp");
        subscribe(unmatched, "sensor/humidity");
        byte[] publishPacket = publishPacket(0x30, "sensor/temp", "21");

        publisher.writeInbound(Unpooled.wrappedBuffer(publishPacket));

        assertArrayEquals(publishPacket, readOutbound(first));
        assertArrayEquals(publishPacket, readOutbound(second));
        assertTrue(unmatched.outboundMessages().isEmpty());
        publisher.finishAndReleaseAll();
        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
        unmatched.finishAndReleaseAll();
    }

    @Test
    void shouldNotQueueQosZeroMessageForOfflinePersistentSession() {
        MqttBroker broker = new MqttBroker();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", false);
        subscribe(subscriber, "sensor/temp");
        subscriber.close().syncUninterruptibly();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);

        publisher.writeInbound(Unpooled.wrappedBuffer(publishPacket(0x30, "sensor/temp", "21")));
        EmbeddedChannel reconnected = connectedChannel(broker, "subscriber", false);

        assertTrue(reconnected.outboundMessages().isEmpty());
        subscriber.finishAndReleaseAll();
        publisher.finishAndReleaseAll();
        reconnected.finishAndReleaseAll();
    }

    @Test
    void shouldSupportEmptyQosZeroPayload() {
        MqttBroker broker = new MqttBroker();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "event/empty");
        byte[] publishPacket = publishPacket(0x30, "event/empty", "");

        publisher.writeInbound(Unpooled.wrappedBuffer(publishPacket));

        assertArrayEquals(publishPacket, readOutbound(subscriber));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldCloseUnsupportedQosOnePublish() {
        MqttBroker broker = new MqttBroker();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        MqttConnection connection = publisher.pipeline().get(MqttConnection.class);
        byte[] qosOnePublish = {
                0x32, 0x10,
                0x00, 0x0b, 's', 'e', 'n', 's', 'o', 'r', '/', 't', 'e', 'm', 'p',
                0x00, 0x01,
                'x'
        };

        publisher.writeInbound(Unpooled.wrappedBuffer(qosOnePublish));

        assertFalse(publisher.isActive());
        assertEquals(MqttConnectionCloseReason.UNSUPPORTED_FEATURE, connection.closeReason());
        publisher.finishAndReleaseAll();
    }

    @Test
    void shouldCloseRetainedPublishUntilRetainMilestone() {
        MqttBroker broker = new MqttBroker();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        MqttConnection connection = publisher.pipeline().get(MqttConnection.class);

        publisher.writeInbound(Unpooled.wrappedBuffer(publishPacket(0x31, "sensor/temp", "21")));

        assertFalse(publisher.isActive());
        assertEquals(MqttConnectionCloseReason.UNSUPPORTED_FEATURE, connection.closeReason());
        publisher.finishAndReleaseAll();
    }

    private static EmbeddedChannel connectedChannel(
            MqttBroker broker,
            String clientId,
            boolean cleanSession) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(new MqttConnection(broker));
        byte[] clientIdBytes = clientId.getBytes(StandardCharsets.UTF_8);
        ByteBuf connect = Unpooled.buffer(14 + clientIdBytes.length);
        connect.writeByte(0x10);
        connect.writeByte(12 + clientIdBytes.length);
        connect.writeShort(4).writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
        connect.writeByte(4);
        connect.writeByte(cleanSession ? 0x02 : 0x00);
        connect.writeShort(60);
        connect.writeShort(clientIdBytes.length).writeBytes(clientIdBytes);
        channel.writeInbound(connect);
        readOutbound(channel);
        return channel;
    }

    private static void subscribe(EmbeddedChannel channel, String topicName) {
        byte[] topicBytes = topicName.getBytes(StandardCharsets.UTF_8);
        ByteBuf subscribe = Unpooled.buffer(7 + topicBytes.length);
        subscribe.writeByte(0x82);
        subscribe.writeByte(5 + topicBytes.length);
        subscribe.writeShort(1);
        subscribe.writeShort(topicBytes.length).writeBytes(topicBytes);
        subscribe.writeByte(0);
        channel.writeInbound(subscribe);
        readOutbound(channel);
    }

    private static byte[] publishPacket(int fixedHeader, String topicName, String payload) {
        byte[] topicBytes = topicName.getBytes(StandardCharsets.UTF_8);
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuf publish = Unpooled.buffer(4 + topicBytes.length + payloadBytes.length);
        publish.writeByte(fixedHeader);
        publish.writeByte(2 + topicBytes.length + payloadBytes.length);
        publish.writeShort(topicBytes.length).writeBytes(topicBytes);
        publish.writeBytes(payloadBytes);
        byte[] packet = new byte[publish.readableBytes()];
        publish.readBytes(packet);
        publish.release();
        return packet;
    }

    private static byte[] readOutbound(EmbeddedChannel channel) {
        ByteBuf buffer = channel.readOutbound();
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }
}
