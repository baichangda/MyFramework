package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.authentication.AnonymousMqttAuthenticator;
import cn.bcd.app.mqtt.server.authorization.AllowAllMqttAuthorizer;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizationAction;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizer;
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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttAuthorizationTest {

    @Test
    void shouldCloseWithoutAckOrRoutingWhenPublishIsDenied() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel subscriber = connectedChannel(
                broker, new AllowAllMqttAuthorizer(), "subscriber", null, true);
        subscribe(subscriber, 1, "private/topic", 0);
        MqttAuthorizer denyPublish = request ->
                request.action() != MqttAuthorizationAction.PUBLISH;
        EmbeddedChannel publisher = connectedChannel(
                broker, denyPublish, "publisher", null, true);
        MqttConnection connection = publisher.pipeline().get(MqttConnection.class);

        publisher.writeInbound(publishPacket("private/topic", "value", 1));

        assertFalse(publisher.isActive());
        assertTrue(publisher.outboundMessages().isEmpty());
        assertTrue(subscriber.outboundMessages().isEmpty());
        assertEquals(MqttConnectionCloseReason.AUTHORIZATION_FAILED,
                connection.closeReason());
        publisher.finishAndReleaseAll();
        subscriber.finishAndReleaseAll();
    }

    @Test
    void shouldReturnFailureOnlyForDeniedSubscriptions() {
        MqttBroker broker = MqttTestBroker.create();
        MqttAuthorizer publicSubscriptionsOnly = request ->
                request.action() != MqttAuthorizationAction.SUBSCRIBE
                        || request.topic().startsWith("public/");
        EmbeddedChannel channel = connectedChannel(
                broker, publicSubscriptionsOnly, "cid", null, true);

        channel.writeInbound(subscribePacket(
                7,
                new Subscription("public/#", 1),
                new Subscription("private/#", 0)));

        assertArrayEquals(
                new byte[]{(byte) 0x90, 0x04, 0x00, 0x07, 0x01, (byte) 0x80},
                readOutbound(channel));
        MqttSession session = broker.findSession("cid").orElseThrow();
        assertTrue(session.findSubscription("public/#").isPresent());
        assertTrue(session.findSubscription("private/#").isEmpty());
        assertTrue(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldRefuseConnectWhenWillTopicIsDenied() {
        MqttBroker broker = MqttTestBroker.create();
        MqttAuthorizer denyPublish = request ->
                request.action() != MqttAuthorizationAction.PUBLISH;
        EmbeddedChannel channel = newChannel(broker, denyPublish);

        channel.writeInbound(connectPacket(
                "cid", null, true, "private/will", "offline"));

        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x05}, readOutbound(channel));
        assertFalse(channel.isActive());
        assertTrue(broker.findSession("cid").isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldNotResumePersistentSessionUnderDifferentUsername() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel first = newChannel(broker, new AllowAllMqttAuthorizer());
        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x00}, connect(
                first, "cid", "first-user", false));
        subscribe(first, 1, "private/#", 1);
        MqttSession firstSession = broker.findSession("cid").orElseThrow();
        first.close().syncUninterruptibly();

        EmbeddedChannel second = newChannel(broker, new AllowAllMqttAuthorizer());
        byte[] connAck = connect(second, "cid", "second-user", false);

        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x00}, connAck);
        MqttSession secondSession = broker.findSession("cid").orElseThrow();
        assertNotSame(firstSession, secondSession);
        assertTrue(secondSession.subscriptions().isEmpty());
        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
    }

    private static EmbeddedChannel connectedChannel(
            MqttBroker broker,
            MqttAuthorizer authorizer,
            String clientId,
            String username,
            boolean cleanSession) {
        EmbeddedChannel channel = newChannel(broker, authorizer);
        connect(channel, clientId, username, cleanSession);
        return channel;
    }

    private static EmbeddedChannel newChannel(
            MqttBroker broker,
            MqttAuthorizer authorizer) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(new MqttConnection(
                broker, new AnonymousMqttAuthenticator(), authorizer));
        return channel;
    }

    private static byte[] connect(
            EmbeddedChannel channel,
            String clientId,
            String username,
            boolean cleanSession) {
        channel.writeInbound(connectPacket(
                clientId, username, cleanSession, null, null));
        return readOutbound(channel);
    }

    private static ByteBuf connectPacket(
            String clientId,
            String username,
            boolean cleanSession,
            String willTopic,
            String willPayload) {
        byte[] clientIdBytes = bytes(clientId);
        byte[] usernameBytes = username == null ? new byte[0] : bytes(username);
        byte[] willTopicBytes = willTopic == null ? new byte[0] : bytes(willTopic);
        byte[] willPayloadBytes = willPayload == null ? new byte[0] : bytes(willPayload);
        int remainingLength = 12 + clientIdBytes.length;
        if (willTopic != null) {
            remainingLength += 4 + willTopicBytes.length + willPayloadBytes.length;
        }
        if (username != null) {
            remainingLength += 2 + usernameBytes.length;
        }
        ByteBuf connect = Unpooled.buffer(2 + remainingLength);
        connect.writeByte(0x10);
        connect.writeByte(remainingLength);
        connect.writeShort(4).writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
        connect.writeByte(4);
        int flags = cleanSession ? 0x02 : 0;
        if (willTopic != null) {
            flags |= 0x04;
        }
        if (username != null) {
            flags |= 0x80;
        }
        connect.writeByte(flags);
        connect.writeShort(60);
        connect.writeShort(clientIdBytes.length).writeBytes(clientIdBytes);
        if (willTopic != null) {
            connect.writeShort(willTopicBytes.length).writeBytes(willTopicBytes);
            connect.writeShort(willPayloadBytes.length).writeBytes(willPayloadBytes);
        }
        if (username != null) {
            connect.writeShort(usernameBytes.length).writeBytes(usernameBytes);
        }
        return connect;
    }

    private static void subscribe(
            EmbeddedChannel channel,
            int packetId,
            String topicFilter,
            int qos) {
        channel.writeInbound(subscribePacket(
                packetId, new Subscription(topicFilter, qos)));
        readOutbound(channel);
    }

    private static ByteBuf subscribePacket(
            int packetId,
            Subscription... subscriptions) {
        int payloadLength = 0;
        for (Subscription subscription : subscriptions) {
            payloadLength += 3 + bytes(subscription.topicFilter()).length;
        }
        ByteBuf packet = Unpooled.buffer(4 + payloadLength);
        packet.writeByte(0x82);
        packet.writeByte(2 + payloadLength);
        packet.writeShort(packetId);
        for (Subscription subscription : subscriptions) {
            byte[] topicFilter = bytes(subscription.topicFilter());
            packet.writeShort(topicFilter.length).writeBytes(topicFilter);
            packet.writeByte(subscription.qos());
        }
        return packet;
    }

    private static ByteBuf publishPacket(
            String topicName,
            String payload,
            int packetId) {
        byte[] topicBytes = bytes(topicName);
        byte[] payloadBytes = bytes(payload);
        ByteBuf packet = Unpooled.buffer(6 + topicBytes.length + payloadBytes.length);
        packet.writeByte(0x32);
        packet.writeByte(4 + topicBytes.length + payloadBytes.length);
        packet.writeShort(topicBytes.length).writeBytes(topicBytes);
        packet.writeShort(packetId);
        packet.writeBytes(payloadBytes);
        return packet;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readOutbound(EmbeddedChannel channel) {
        ByteBuf buffer = channel.readOutbound();
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }

    private record Subscription(String topicFilter, int qos) {
    }
}
