package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.support.MqttTestBroker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttUnsubscribeTest {

    private static final String EXACT_TOPIC = "sensor/temp";

    @Test
    void shouldRemoveSubscriptionAndReturnUnsubAck() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = connectedChannel(broker, "cid", true);
        subscribe(channel, 1, EXACT_TOPIC);

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
                (byte) 0xa2, 0x0f,
                0x00, 0x07,
                0x00, 0x0b, 's', 'e', 'n', 's', 'o', 'r', '/', 't', 'e', 'm', 'p'
        }));

        assertArrayEquals(new byte[]{(byte) 0xb0, 0x02, 0x00, 0x07}, readOutbound(channel));
        assertTrue(broker.findSession("cid").orElseThrow().subscriptions().isEmpty());

        publish(channel, EXACT_TOPIC);
        assertTrue(channel.outboundMessages().isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldAcknowledgeUnknownTopicFilter() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = connectedChannel(broker, "cid", true);

        unsubscribe(channel, 9, "unknown/topic");

        assertArrayEquals(new byte[]{(byte) 0xb0, 0x02, 0x00, 0x09}, readOutbound(channel));
        assertTrue(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldOnlyRemoveRequestedFilterWhenSubscriptionsOverlap() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = connectedChannel(broker, "cid", true);
        subscribe(channel, 1, EXACT_TOPIC);
        subscribe(channel, 2, "sensor/#");

        unsubscribe(channel, 3, EXACT_TOPIC);
        readOutbound(channel);

        MqttSession session = broker.findSession("cid").orElseThrow();
        assertFalse(session.findSubscription(EXACT_TOPIC).isPresent());
        assertTrue(session.findSubscription("sensor/#").isPresent());
        publish(channel, EXACT_TOPIC);
        assertFalse(channel.outboundMessages().isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldKeepUnsubscribeResultWhenPersistentSessionReconnects() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel first = connectedChannel(broker, "cid", false);
        subscribe(first, 1, EXACT_TOPIC);
        MqttSession session = broker.findSession("cid").orElseThrow();
        unsubscribe(first, 2, EXACT_TOPIC);
        readOutbound(first);
        first.close().syncUninterruptibly();

        EmbeddedChannel second = connectedChannel(broker, "cid", false);

        assertSame(session, broker.findSession("cid").orElseThrow());
        assertTrue(session.subscriptions().isEmpty());
        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
    }

    @Test
    void shouldCloseWithoutPartialRemovalWhenAFilterIsInvalid() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = connectedChannel(broker, "cid", false);
        subscribe(channel, 1, EXACT_TOPIC);
        subscribe(channel, 2, "other/topic");
        MqttSession session = broker.findSession("cid").orElseThrow();
        MqttConnection connection = channel.pipeline().get(MqttConnection.class);

        channel.writeInbound(MqttMessageBuilders.unsubscribe()
                .messageId(3)
                .addTopicFilter(EXACT_TOPIC)
                .addTopicFilter("invalid/#/filter")
                .build());

        assertFalse(channel.isActive());
        assertTrue(session.findSubscription(EXACT_TOPIC).isPresent());
        assertTrue(session.findSubscription("other/topic").isPresent());
        assertSame(MqttConnectionCloseReason.PROTOCOL_ERROR, connection.closeReason());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldCloseWhenUnsubscribePayloadIsEmpty() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = connectedChannel(broker, "cid", true);
        MqttConnection connection = channel.pipeline().get(MqttConnection.class);

        channel.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{(byte) 0xa2, 0x02, 0x00, 0x01}));

        assertFalse(channel.isActive());
        assertSame(MqttConnectionCloseReason.PROTOCOL_ERROR, connection.closeReason());
        assertTrue(channel.outboundMessages().isEmpty());
        channel.finishAndReleaseAll();
    }

    private static void subscribe(
            EmbeddedChannel channel,
            int packetId,
            String topicFilter) {
        channel.writeInbound(MqttMessageBuilders.subscribe()
                .messageId(packetId)
                .addSubscription(MqttQoS.AT_MOST_ONCE, topicFilter)
                .build());
        readOutbound(channel);
    }

    private static void unsubscribe(
            EmbeddedChannel channel,
            int packetId,
            String topicFilter) {
        channel.writeInbound(MqttMessageBuilders.unsubscribe()
                .messageId(packetId)
                .addTopicFilter(topicFilter)
                .build());
    }

    private static void publish(EmbeddedChannel channel, String topicName) {
        channel.writeInbound(MqttMessageBuilders.publish()
                .topicName(topicName)
                .qos(MqttQoS.AT_MOST_ONCE)
                .payload(Unpooled.wrappedBuffer(new byte[]{0x01}))
                .build());
    }

    private static EmbeddedChannel connectedChannel(
            MqttBroker broker,
            String clientId,
            boolean cleanSession) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(new MqttConnection(broker));
        ByteBuf connect = Unpooled.buffer();
        connect.writeByte(0x10);
        connect.writeByte(12 + clientId.length());
        connect.writeShort(4);
        connect.writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
        connect.writeByte(4);
        connect.writeByte(cleanSession ? 0x02 : 0x00);
        connect.writeShort(60);
        connect.writeShort(clientId.length());
        connect.writeCharSequence(clientId, java.nio.charset.StandardCharsets.UTF_8);
        channel.writeInbound(connect);
        readOutbound(channel);
        return channel;
    }

    private static byte[] readOutbound(EmbeddedChannel channel) {
        ByteBuf buffer = channel.readOutbound();
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }
}
