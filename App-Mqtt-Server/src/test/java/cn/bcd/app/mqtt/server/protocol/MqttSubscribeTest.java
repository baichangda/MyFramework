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
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttSubscribeTest {

    private static final byte[] EXACT_TOPIC_SUBSCRIBE = {
            (byte) 0x82, 0x10,
            0x00, 0x07,
            0x00, 0x0b, 's', 'e', 'n', 's', 'o', 'r', '/', 't', 'e', 'm', 'p',
            0x01
    };

    @Test
    void shouldStoreExactTopicSubscriptionAndReturnSubAck() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = connectedChannel(broker, true);

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(EXACT_TOPIC_SUBSCRIBE)));

        assertArrayEquals(new byte[]{(byte) 0x90, 0x03, 0x00, 0x07, 0x01}, readOutbound(channel));
        assertEquals(MqttQoS.AT_LEAST_ONCE, broker.findSession("cid")
                .orElseThrow()
                .findSubscription("sensor/temp")
                .orElseThrow()
                .qos());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldAcknowledgeAndStoreExactAndWildcardSubscriptions() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = connectedChannel(broker, true);
        byte[] subscribe = {
                (byte) 0x82, 0x13,
                0x00, 0x09,
                0x00, 0x03, 'a', '/', 'b', 0x00,
                0x00, 0x08, 's', 'e', 'n', 's', 'o', 'r', '/', '+', 0x01
        };

        channel.writeInbound(Unpooled.wrappedBuffer(subscribe));

        assertArrayEquals(new byte[]{(byte) 0x90, 0x04, 0x00, 0x09, 0x00, 0x01},
                readOutbound(channel));
        MqttSession session = broker.findSession("cid").orElseThrow();
        assertTrue(session.findSubscription("a/b").isPresent());
        assertTrue(session.findSubscription("sensor/+").isPresent());
        assertTrue(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldReplaceQosWhenSameTopicIsSubscribedAgain() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = connectedChannel(broker, true);
        channel.writeInbound(Unpooled.wrappedBuffer(EXACT_TOPIC_SUBSCRIBE));
        readOutbound(channel);
        byte[] resubscribe = EXACT_TOPIC_SUBSCRIBE.clone();
        resubscribe[resubscribe.length - 1] = 0x02;

        channel.writeInbound(Unpooled.wrappedBuffer(resubscribe));

        assertArrayEquals(new byte[]{(byte) 0x90, 0x03, 0x00, 0x07, 0x02}, readOutbound(channel));
        assertEquals(MqttQoS.EXACTLY_ONCE, broker.findSession("cid")
                .orElseThrow()
                .findSubscription("sensor/temp")
                .orElseThrow()
                .qos());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldKeepSubscriptionsWhenPersistentSessionReconnects() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel first = connectedChannel(broker, false);
        first.writeInbound(Unpooled.wrappedBuffer(EXACT_TOPIC_SUBSCRIBE));
        readOutbound(first);
        MqttSession session = broker.findSession("cid").orElseThrow();
        first.close().syncUninterruptibly();

        EmbeddedChannel second = connectedChannel(broker, false);

        assertSame(session, broker.findSession("cid").orElseThrow());
        assertTrue(session.findSubscription("sensor/temp").isPresent());
        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
    }

    @Test
    void shouldDiscardSubscriptionsWhenCleanSessionCloses() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel first = connectedChannel(broker, true);
        first.writeInbound(Unpooled.wrappedBuffer(EXACT_TOPIC_SUBSCRIBE));
        readOutbound(first);

        first.close().syncUninterruptibly();
        EmbeddedChannel second = connectedChannel(broker, true);

        assertTrue(broker.findSession("cid").orElseThrow().subscriptions().isEmpty());
        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
    }

    @Test
    void shouldCloseWhenSubscribePayloadIsEmpty() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = connectedChannel(broker, true);
        MqttConnection connection = channel.pipeline().get(MqttConnection.class);

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0x82, 0x02, 0x00, 0x01}));

        assertFalse(channel.isActive());
        assertEquals(MqttConnectionCloseReason.PROTOCOL_ERROR,
                connection.closeReason());
        assertTrue(channel.outboundMessages().isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldCloseWhenWildcardPlacementIsInvalid() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = connectedChannel(broker, true);
        MqttConnection connection = channel.pipeline().get(MqttConnection.class);
        byte[] invalidSubscribe = {
                (byte) 0x82, 0x12,
                0x00, 0x02,
                0x00, 0x0d, 's', 'e', 'n', 's', 'o', 'r', '/', '#', '/', 't', 'e', 'm', 'p',
                0x00
        };

        channel.writeInbound(Unpooled.wrappedBuffer(invalidSubscribe));

        assertFalse(channel.isActive());
        assertEquals(MqttConnectionCloseReason.PROTOCOL_ERROR, connection.closeReason());
        channel.finishAndReleaseAll();
    }

    private static EmbeddedChannel connectedChannel(MqttBroker broker, boolean cleanSession) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(new MqttConnection(broker));
        byte flags = cleanSession ? (byte) 0x02 : 0x00;
        byte[] connect = {
                0x10, 0x0f,
                0x00, 0x04, 'M', 'Q', 'T', 'T',
                0x04, flags, 0x00, 0x3c,
                0x00, 0x03, 'c', 'i', 'd'
        };
        channel.writeInbound(Unpooled.wrappedBuffer(connect));
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
