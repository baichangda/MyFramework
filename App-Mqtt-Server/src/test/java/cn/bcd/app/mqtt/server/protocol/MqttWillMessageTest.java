package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.support.MqttTestBroker;
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

class MqttWillMessageTest {

    @Test
    void shouldPublishWillWhenNetworkCloses() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "status/source", 0);
        EmbeddedChannel publisher = connectedWithWill(
                broker, "source", true, "status/source", "offline", 0, false);

        publisher.close().syncUninterruptibly();

        assertArrayEquals(
                publishPacket(0x30, "status/source", "offline", 0),
                readOutbound(subscriber));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldCancelWillOnNormalDisconnect() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "status/source", 0);
        EmbeddedChannel publisher = connectedWithWill(
                broker, "source", true, "status/source", "offline", 0, false);
        MqttConnection connection = publisher.pipeline().get(MqttConnection.class);

        publisher.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{(byte) 0xe0, 0x00}));

        assertTrue(subscriber.outboundMessages().isEmpty());
        assertEquals(MqttConnectionCloseReason.NORMAL_DISCONNECT,
                connection.closeReason());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldPublishWillOnProtocolError() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "status/source", 0);
        EmbeddedChannel publisher = connectedWithWill(
                broker, "source", true, "status/source", "offline", 0, false);
        MqttConnection connection = publisher.pipeline().get(MqttConnection.class);

        publisher.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{(byte) 0xd0, 0x00}));

        assertArrayEquals(
                publishPacket(0x30, "status/source", "offline", 0),
                readOutbound(subscriber));
        assertEquals(MqttConnectionCloseReason.PROTOCOL_ERROR,
                connection.closeReason());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldQueueQosOneWillForOfflinePersistentSubscriber() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", false);
        subscribe(subscriber, "status/source", 1);
        subscriber.close().syncUninterruptibly();
        EmbeddedChannel publisher = connectedWithWill(
                broker, "source", true, "status/source", "offline", 1, false);

        publisher.close().syncUninterruptibly();
        EmbeddedChannel reconnected = connectedChannel(broker, "subscriber", false);

        assertArrayEquals(
                publishPacket(0x32, "status/source", "offline", 1),
                readOutbound(reconnected));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
        reconnected.finishAndReleaseAll();
    }

    @Test
    void shouldDeliverWillWithQosTwo() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "status/source", 2);
        EmbeddedChannel publisher = connectedWithWill(
                broker, "source", true, "status/source", "offline", 2, false);

        publisher.close().syncUninterruptibly();

        assertArrayEquals(
                publishPacket(0x34, "status/source", "offline", 1),
                readOutbound(subscriber));
        assertEquals(1, broker.findSession("subscriber").orElseThrow()
                .pendingPublishes().size());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldStoreRetainedWillForLaterSubscribers() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedWithWill(
                broker, "source", true, "status/source", "offline", 0, true);
        publisher.close().syncUninterruptibly();

        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "status/source", 0);

        assertArrayEquals(
                publishPacket(0x31, "status/source", "offline", 0),
                readOutbound(subscriber));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldPublishOldConnectionWillWhenClientIdIsTakenOver() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "status/source", 0);
        EmbeddedChannel first = connectedWithWill(
                broker, "source", true, "status/source", "offline", 0, false);

        EmbeddedChannel second = connectedChannel(broker, "source", true);
        first.runPendingTasks();

        assertFalse(first.isActive());
        assertTrue(second.isActive());
        assertArrayEquals(
                publishPacket(0x30, "status/source", "offline", 0),
                readOutbound(subscriber));
        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldCloseWhenWillQosIsReserved() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = newChannel(broker);
        MqttConnection connection = channel.pipeline().get(MqttConnection.class);

        channel.writeInbound(connectPacket(
                "source", true, "status/source", "offline", 3, false));

        assertFalse(channel.isActive());
        assertTrue(channel.outboundMessages().isEmpty());
        assertEquals(MqttConnectionCloseReason.PROTOCOL_ERROR,
                connection.closeReason());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldCloseWhenWillRetainIsSetWithoutWillFlag() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = newChannel(broker);
        MqttConnection connection = channel.pipeline().get(MqttConnection.class);
        ByteBuf connect = connectPacket("source", true, null, null, 0, false);
        connect.setByte(9, 0x22);

        channel.writeInbound(connect);

        assertFalse(channel.isActive());
        assertTrue(channel.outboundMessages().isEmpty());
        assertEquals(MqttConnectionCloseReason.PROTOCOL_ERROR,
                connection.closeReason());
        channel.finishAndReleaseAll();
    }

    private static EmbeddedChannel connectedWithWill(
            MqttBroker broker,
            String clientId,
            boolean cleanSession,
            String willTopic,
            String willPayload,
            int willQos,
            boolean willRetained) {
        EmbeddedChannel channel = newChannel(broker);
        channel.writeInbound(connectPacket(
                clientId, cleanSession, willTopic, willPayload, willQos, willRetained));
        readOutbound(channel);
        return channel;
    }

    private static EmbeddedChannel connectedChannel(
            MqttBroker broker,
            String clientId,
            boolean cleanSession) {
        EmbeddedChannel channel = newChannel(broker);
        channel.writeInbound(connectPacket(
                clientId, cleanSession, null, null, 0, false));
        readOutbound(channel);
        return channel;
    }

    private static EmbeddedChannel newChannel(MqttBroker broker) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(new MqttConnection(broker));
        return channel;
    }

    private static ByteBuf connectPacket(
            String clientId,
            boolean cleanSession,
            String willTopic,
            String willPayload,
            int willQos,
            boolean willRetained) {
        byte[] clientIdBytes = clientId.getBytes(StandardCharsets.UTF_8);
        byte[] topicBytes = willTopic == null
                ? new byte[0]
                : willTopic.getBytes(StandardCharsets.UTF_8);
        byte[] payloadBytes = willPayload == null
                ? new byte[0]
                : willPayload.getBytes(StandardCharsets.UTF_8);
        boolean hasWill = willTopic != null;
        int remainingLength = 12 + clientIdBytes.length;
        if (hasWill) {
            remainingLength += 4 + topicBytes.length + payloadBytes.length;
        }
        ByteBuf connect = Unpooled.buffer(2 + remainingLength);
        connect.writeByte(0x10);
        connect.writeByte(remainingLength);
        connect.writeShort(4).writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
        connect.writeByte(4);
        int flags = cleanSession ? 0x02 : 0;
        if (hasWill) {
            flags |= 0x04 | willQos << 3;
            if (willRetained) {
                flags |= 0x20;
            }
        }
        connect.writeByte(flags);
        connect.writeShort(60);
        connect.writeShort(clientIdBytes.length).writeBytes(clientIdBytes);
        if (hasWill) {
            connect.writeShort(topicBytes.length).writeBytes(topicBytes);
            connect.writeShort(payloadBytes.length).writeBytes(payloadBytes);
        }
        return connect;
    }

    private static void subscribe(
            EmbeddedChannel channel,
            String topicFilter,
            int qos) {
        byte[] topicBytes = topicFilter.getBytes(StandardCharsets.UTF_8);
        ByteBuf subscribe = Unpooled.buffer(7 + topicBytes.length);
        subscribe.writeByte(0x82);
        subscribe.writeByte(5 + topicBytes.length);
        subscribe.writeShort(1);
        subscribe.writeShort(topicBytes.length).writeBytes(topicBytes);
        subscribe.writeByte(qos);
        channel.writeInbound(subscribe);
        readOutbound(channel);
    }

    private static byte[] publishPacket(
            int fixedHeader,
            String topicName,
            String payload,
            int packetId) {
        byte[] topicBytes = topicName.getBytes(StandardCharsets.UTF_8);
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        int packetIdLength = packetId == 0 ? 0 : 2;
        ByteBuf publish = Unpooled.buffer(
                4 + topicBytes.length + packetIdLength + payloadBytes.length);
        publish.writeByte(fixedHeader);
        publish.writeByte(2 + topicBytes.length + packetIdLength + payloadBytes.length);
        publish.writeShort(topicBytes.length).writeBytes(topicBytes);
        if (packetId != 0) {
            publish.writeShort(packetId);
        }
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
