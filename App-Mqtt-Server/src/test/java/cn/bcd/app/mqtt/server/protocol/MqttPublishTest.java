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
import java.util.HexFormat;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttPublishTest {

    @Test
    void shouldRouteQosZeroPublishToExactTopicSubscriber() {
        MqttBroker broker = MqttTestBroker.create();
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
        MqttBroker broker = MqttTestBroker.create();
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
    void shouldRouteSingleAndMultiLevelWildcardSubscriptions() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel singleLevel = connectedChannel(broker, "single", true);
        EmbeddedChannel multiLevel = connectedChannel(broker, "multi", true);
        subscribe(singleLevel, "sensor/+/temperature");
        subscribe(multiLevel, "sensor/#");
        byte[] publishPacket = publishPacket(0x30, "sensor/room1/temperature", "21");

        publisher.writeInbound(Unpooled.wrappedBuffer(publishPacket));

        assertArrayEquals(publishPacket, readOutbound(singleLevel));
        assertArrayEquals(publishPacket, readOutbound(multiLevel));
        publisher.finishAndReleaseAll();
        singleLevel.finishAndReleaseAll();
        multiLevel.finishAndReleaseAll();
    }

    @Test
    void shouldDeliverOnceWhenMultipleFiltersOfOneClientMatch() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/+/temperature");
        subscribe(subscriber, "sensor/#");
        byte[] publishPacket = publishPacket(0x30, "sensor/room1/temperature", "21");

        publisher.writeInbound(Unpooled.wrappedBuffer(publishPacket));

        assertArrayEquals(publishPacket, readOutbound(subscriber));
        assertTrue(subscriber.outboundMessages().isEmpty());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldKeepRootWildcardSeparateFromSystemTopics() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel rootWildcard = connectedChannel(broker, "root", true);
        EmbeddedChannel systemWildcard = connectedChannel(broker, "system", true);
        subscribe(rootWildcard, "#");
        subscribe(systemWildcard, "$SYS/#");
        byte[] publishPacket = publishPacket(0x30, "$SYS/broker/uptime", "10");

        publisher.writeInbound(Unpooled.wrappedBuffer(publishPacket));

        assertTrue(rootWildcard.outboundMessages().isEmpty());
        assertArrayEquals(publishPacket, readOutbound(systemWildcard));
        publisher.finishAndReleaseAll();
        rootWildcard.finishAndReleaseAll();
        systemWildcard.finishAndReleaseAll();
    }

    @Test
    void shouldNotQueueQosZeroMessageForOfflinePersistentSession() {
        MqttBroker broker = MqttTestBroker.create();
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
        MqttBroker broker = MqttTestBroker.create();
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
    void shouldAcknowledgeQosOneAndDowngradeForQosZeroSubscription() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp");

        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosOnePublishPacket(0x32, "sensor/temp", 7, "21")));

        assertArrayEquals(new byte[]{0x40, 0x02, 0x00, 0x07}, readOutbound(publisher));
        assertArrayEquals(publishPacket(0x30, "sensor/temp", "21"), readOutbound(subscriber));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldTrackQosOneUntilSubscriberPubAck() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp", 1);

        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosOnePublishPacket(0x32, "sensor/temp", 7, "21")));
        readOutbound(publisher);

        assertArrayEquals(
                qosOnePublishPacket(0x32, "sensor/temp", 1, "21"), readOutbound(subscriber));
        assertEquals(1, broker.findSession("subscriber").orElseThrow()
                .pendingPublishes().size());
        subscriber.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{0x40, 0x02, 0x00, 0x01}));
        assertTrue(broker.findSession("subscriber").orElseThrow()
                .pendingPublishes().isEmpty());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldRetransmitUnacknowledgedPublishWithDupAfterPersistentReconnect() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", false);
        subscribe(subscriber, "sensor/temp", 1);
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosOnePublishPacket(0x32, "sensor/temp", 7, "21")));
        readOutbound(publisher);
        readOutbound(subscriber);
        subscriber.close().syncUninterruptibly();

        EmbeddedChannel reconnected = connectedChannel(broker, "subscriber", false);

        assertArrayEquals(
                qosOnePublishPacket(0x3a, "sensor/temp", 1, "21"),
                readOutbound(reconnected));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
        reconnected.finishAndReleaseAll();
    }

    @Test
    void shouldSendOfflineQueuedQosOneWithoutDupOnFirstDelivery() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", false);
        subscribe(subscriber, "sensor/temp", 1);
        subscriber.close().syncUninterruptibly();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosOnePublishPacket(0x32, "sensor/temp", 7, "21")));
        readOutbound(publisher);

        EmbeddedChannel reconnected = connectedChannel(broker, "subscriber", false);

        assertArrayEquals(
                qosOnePublishPacket(0x32, "sensor/temp", 1, "21"),
                readOutbound(reconnected));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
        reconnected.finishAndReleaseAll();
    }

    @Test
    void shouldStoreRetainedPublishAndDeliverItToNewSubscriber() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "sensor/temp", "21")));

        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp");

        assertArrayEquals(publishPacket(0x31, "sensor/temp", "21"), readOutbound(subscriber));
        assertTrue(subscriber.outboundMessages().isEmpty());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldStoreAndDeliverQosOneRetainedPublish() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosOnePublishPacket(0x33, "sensor/temp", 7, "21")));
        assertArrayEquals(new byte[]{0x40, 0x02, 0x00, 0x07}, readOutbound(publisher));

        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp", 1);

        assertArrayEquals(
                qosOnePublishPacket(0x33, "sensor/temp", 1, "21"),
                readOutbound(subscriber));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldAcknowledgeDuplicateQosOnePublish() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);

        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosOnePublishPacket(0x3a, "sensor/temp", 7, "21")));

        assertArrayEquals(new byte[]{0x40, 0x02, 0x00, 0x07}, readOutbound(publisher));
        assertTrue(publisher.isActive());
        publisher.finishAndReleaseAll();
    }

    @Test
    void shouldCompleteQosTwoHandshakeInBothDirections() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp", 2);

        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosPublishPacket(0x34, "sensor/temp", 7, "21")));
        assertArrayEquals(new byte[]{0x50, 0x02, 0x00, 0x07}, readOutbound(publisher));
        assertTrue(subscriber.outboundMessages().isEmpty());

        publisher.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{0x62, 0x02, 0x00, 0x07}));
        assertArrayEquals(new byte[]{0x70, 0x02, 0x00, 0x07}, readOutbound(publisher));
        assertArrayEquals(
                qosPublishPacket(0x34, "sensor/temp", 1, "21"), readOutbound(subscriber));

        subscriber.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{0x50, 0x02, 0x00, 0x01}));
        assertArrayEquals(new byte[]{0x62, 0x02, 0x00, 0x01}, readOutbound(subscriber));
        subscriber.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{0x70, 0x02, 0x00, 0x01}));
        assertTrue(broker.findSession("subscriber").orElseThrow()
                .pendingPublishes().isEmpty());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldNotRouteDuplicateQosTwoPublishTwice() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp");

        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosPublishPacket(0x34, "sensor/temp", 7, "21")));
        readOutbound(publisher);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosPublishPacket(0x3c, "sensor/temp", 7, "21")));
        assertArrayEquals(new byte[]{0x50, 0x02, 0x00, 0x07}, readOutbound(publisher));
        publisher.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{0x62, 0x02, 0x00, 0x07}));
        readOutbound(publisher);

        assertArrayEquals(publishPacket(0x30, "sensor/temp", "21"), readOutbound(subscriber));
        assertTrue(subscriber.outboundMessages().isEmpty());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldResumeWithPubRelWhenWaitingForPubComp() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", false);
        subscribe(subscriber, "sensor/temp", 2);
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosPublishPacket(0x34, "sensor/temp", 7, "21")));
        readOutbound(publisher);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{0x62, 0x02, 0x00, 0x07}));
        readOutbound(publisher);
        readOutbound(subscriber);
        subscriber.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{0x50, 0x02, 0x00, 0x01}));
        readOutbound(subscriber);
        subscriber.close().syncUninterruptibly();

        EmbeddedChannel reconnected = connectedChannel(broker, "subscriber", false);

        assertArrayEquals(new byte[]{0x62, 0x02, 0x00, 0x01}, readOutbound(reconnected));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
        reconnected.finishAndReleaseAll();
    }

    @Test
    void shouldRetransmitQosTwoPublishWithDupWhenWaitingForPubRec() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", false);
        subscribe(subscriber, "sensor/temp", 2);
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosPublishPacket(0x34, "sensor/temp", 7, "21")));
        readOutbound(publisher);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{0x62, 0x02, 0x00, 0x07}));
        readOutbound(publisher);
        readOutbound(subscriber);
        subscriber.close().syncUninterruptibly();

        EmbeddedChannel reconnected = connectedChannel(broker, "subscriber", false);

        assertArrayEquals(
                qosPublishPacket(0x3c, "sensor/temp", 1, "21"),
                readOutbound(reconnected));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
        reconnected.finishAndReleaseAll();
    }

    @Test
    void shouldContinueInboundQosTwoAfterPersistentPublisherReconnects() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp");
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", false);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosPublishPacket(0x34, "sensor/temp", 7, "21")));
        readOutbound(publisher);
        publisher.close().syncUninterruptibly();

        EmbeddedChannel reconnected = connectedChannel(broker, "publisher", false);
        reconnected.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{0x62, 0x02, 0x00, 0x07}));

        assertArrayEquals(new byte[]{0x70, 0x02, 0x00, 0x07}, readOutbound(reconnected));
        assertArrayEquals(publishPacket(0x30, "sensor/temp", "21"), readOutbound(subscriber));
        publisher.finishAndReleaseAll();
        reconnected.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldStoreQosTwoRetainedMessageOnlyAfterPubRel() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                qosPublishPacket(0x35, "sensor/temp", 7, "21")));
        readOutbound(publisher);
        EmbeddedChannel early = connectedChannel(broker, "early", true);
        subscribe(early, "sensor/temp");
        assertTrue(early.outboundMessages().isEmpty());

        publisher.writeInbound(Unpooled.wrappedBuffer(
                new byte[]{0x62, 0x02, 0x00, 0x07}));
        readOutbound(publisher);
        readOutbound(early);
        EmbeddedChannel late = connectedChannel(broker, "late", true);
        subscribe(late, "sensor/temp", 2);

        assertArrayEquals(
                qosPublishPacket(0x35, "sensor/temp", 1, "21"), readOutbound(late));
        publisher.finishAndReleaseAll();
        early.finishAndReleaseAll();
        late.finishAndReleaseAll();
    }

    @Test
    void shouldClearRetainFlagForLiveDelivery() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp");

        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "sensor/temp", "21")));

        assertArrayEquals(publishPacket(0x30, "sensor/temp", "21"), readOutbound(subscriber));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldOverwriteRetainedMessageForSameTopic() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "sensor/temp", "21")));
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "sensor/temp", "22")));

        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp");

        assertArrayEquals(publishPacket(0x31, "sensor/temp", "22"), readOutbound(subscriber));
        assertTrue(subscriber.outboundMessages().isEmpty());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldDeleteRetainedMessageWithEmptyRetainedPublish() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "sensor/temp", "21")));
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "sensor/temp", "")));

        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp");

        assertTrue(subscriber.outboundMessages().isEmpty());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldNotOverwriteRetainedMessageWithNormalPublish() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "sensor/temp", "retained")));
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x30, "sensor/temp", "live")));

        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/temp");

        assertArrayEquals(
                publishPacket(0x31, "sensor/temp", "retained"), readOutbound(subscriber));
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldDeliverAllRetainedMessagesMatchingWildcardSubscription() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "sensor/room1/temp", "21")));
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "sensor/room2/temp", "22")));
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "event/status", "online")));

        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);
        subscribe(subscriber, "sensor/+/temp");

        Set<String> delivered = Set.of(
                HexFormat.of().formatHex(readOutbound(subscriber)),
                HexFormat.of().formatHex(readOutbound(subscriber)));
        assertEquals(Set.of(
                HexFormat.of().formatHex(
                        publishPacket(0x31, "sensor/room1/temp", "21")),
                HexFormat.of().formatHex(
                        publishPacket(0x31, "sensor/room2/temp", "22"))), delivered);
        assertTrue(subscriber.outboundMessages().isEmpty());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldDeliverRetainedTopicOnceForOverlappingFiltersInOneSubscribe() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel publisher = connectedChannel(broker, "publisher", true);
        publisher.writeInbound(Unpooled.wrappedBuffer(
                publishPacket(0x31, "sensor/room1/temp", "21")));
        EmbeddedChannel subscriber = connectedChannel(broker, "subscriber", true);

        subscribe(subscriber, "sensor/+/temp", "sensor/#");

        assertArrayEquals(
                publishPacket(0x31, "sensor/room1/temp", "21"), readOutbound(subscriber));
        assertTrue(subscriber.outboundMessages().isEmpty());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
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
        subscribe(channel, new String[]{topicName});
    }

    private static void subscribe(EmbeddedChannel channel, String topicName, int qos) {
        byte[] topicBytes = topicName.getBytes(StandardCharsets.UTF_8);
        ByteBuf subscribe = Unpooled.buffer(7 + topicBytes.length);
        subscribe.writeByte(0x82);
        subscribe.writeByte(5 + topicBytes.length);
        subscribe.writeShort(1);
        subscribe.writeShort(topicBytes.length).writeBytes(topicBytes);
        subscribe.writeByte(qos);
        channel.writeInbound(subscribe);
        readOutbound(channel);
    }

    private static void subscribe(EmbeddedChannel channel, String... topicNames) {
        int payloadLength = 0;
        for (String topicName : topicNames) {
            payloadLength += 3 + topicName.getBytes(StandardCharsets.UTF_8).length;
        }
        ByteBuf subscribe = Unpooled.buffer(4 + payloadLength);
        subscribe.writeByte(0x82);
        subscribe.writeByte(2 + payloadLength);
        subscribe.writeShort(1);
        for (String topicName : topicNames) {
            byte[] topicBytes = topicName.getBytes(StandardCharsets.UTF_8);
            subscribe.writeShort(topicBytes.length).writeBytes(topicBytes);
            subscribe.writeByte(0);
        }
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

    private static byte[] qosOnePublishPacket(
            int fixedHeader,
            String topicName,
            int packetId,
            String payload) {
        byte[] topicBytes = topicName.getBytes(StandardCharsets.UTF_8);
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuf publish = Unpooled.buffer(6 + topicBytes.length + payloadBytes.length);
        publish.writeByte(fixedHeader);
        publish.writeByte(4 + topicBytes.length + payloadBytes.length);
        publish.writeShort(topicBytes.length).writeBytes(topicBytes);
        publish.writeShort(packetId);
        publish.writeBytes(payloadBytes);
        byte[] packet = new byte[publish.readableBytes()];
        publish.readBytes(packet);
        publish.release();
        return packet;
    }

    private static byte[] qosPublishPacket(
            int fixedHeader,
            String topicName,
            int packetId,
            String payload) {
        return qosOnePublishPacket(fixedHeader, topicName, packetId, payload);
    }

    private static byte[] readOutbound(EmbeddedChannel channel) {
        ByteBuf buffer = channel.readOutbound();
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }
}
