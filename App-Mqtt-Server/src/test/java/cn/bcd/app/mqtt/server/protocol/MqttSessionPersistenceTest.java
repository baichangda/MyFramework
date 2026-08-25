package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.config.MqttPersistenceProperties;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.retained.InMemoryMqttRetainedMessageStore;
import cn.bcd.app.mqtt.server.session.persistence.SqliteMqttSessionStore;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MqttSessionPersistenceTest {

    @Test
    void shouldRestoreSubscriptionQueueAndDupStateAcrossBrokerRestarts()
            throws IOException {
        Path databasePath = Path.of(
                "build", "tmp", "mqtt-broker-session-" + UUID.randomUUID() + ".db");
        MqttPersistenceProperties properties = properties(databasePath);

        try {
            try (SqliteMqttSessionStore firstStore =
                         new SqliteMqttSessionStore(properties)) {
                MqttBroker firstBroker = broker(firstStore);
                EmbeddedChannel subscriber = newChannel(firstBroker);
                assertArrayEquals(connAck(false), connect(subscriber, "subscriber", false));
                subscribe(subscriber, "sensor/temp", 1);
                subscriber.close().syncUninterruptibly();
                subscriber.finishAndReleaseAll();
            }

            try (SqliteMqttSessionStore secondStore =
                         new SqliteMqttSessionStore(properties)) {
                MqttBroker secondBroker = broker(secondStore);
                EmbeddedChannel publisher = newChannel(secondBroker);
                connect(publisher, "publisher", true);
                publisher.writeInbound(qosOnePublish("sensor/temp", 7, "21"));
                assertArrayEquals(
                        new byte[]{0x40, 0x02, 0x00, 0x07}, readOutbound(publisher));

                EmbeddedChannel subscriber = newChannel(secondBroker);
                assertArrayEquals(connAck(true), connect(subscriber, "subscriber", false));
                assertArrayEquals(
                        qosOnePublishBytes(0x32, "sensor/temp", 1, "21"),
                        readOutbound(subscriber));
                subscriber.close().syncUninterruptibly();
                publisher.finishAndReleaseAll();
                subscriber.finishAndReleaseAll();
            }

            try (SqliteMqttSessionStore thirdStore =
                         new SqliteMqttSessionStore(properties)) {
                MqttBroker thirdBroker = broker(thirdStore);
                EmbeddedChannel subscriber = newChannel(thirdBroker);
                assertArrayEquals(connAck(true), connect(subscriber, "subscriber", false));
                assertArrayEquals(
                        qosOnePublishBytes(0x3a, "sensor/temp", 1, "21"),
                        readOutbound(subscriber));
                subscriber.finishAndReleaseAll();
            }
        } finally {
            Files.deleteIfExists(databasePath);
        }
    }

    private static MqttBroker broker(SqliteMqttSessionStore sessionStore) {
        return new MqttBroker(new InMemoryMqttRetainedMessageStore(), sessionStore);
    }

    private static EmbeddedChannel newChannel(MqttBroker broker) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(new MqttConnection(broker));
        return channel;
    }

    private static byte[] connect(
            EmbeddedChannel channel,
            String clientId,
            boolean cleanSession) {
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
        return readOutbound(channel);
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

    private static ByteBuf qosOnePublish(
            String topicName,
            int packetId,
            String payload) {
        return Unpooled.wrappedBuffer(
                qosOnePublishBytes(0x32, topicName, packetId, payload));
    }

    private static byte[] qosOnePublishBytes(
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

    private static byte[] connAck(boolean sessionPresent) {
        return new byte[]{0x20, 0x02, sessionPresent ? (byte) 0x01 : 0x00, 0x00};
    }

    private static byte[] readOutbound(EmbeddedChannel channel) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        ByteBuf buffer;
        while ((buffer = channel.readOutbound()) == null
                && System.nanoTime() < deadline) {
            channel.runPendingTasks();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        if (buffer == null) {
            throw new AssertionError("Timed out waiting for MQTT packet");
        }
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }

    private static MqttPersistenceProperties properties(Path databasePath) {
        MqttPersistenceProperties properties = new MqttPersistenceProperties();
        properties.getSession().getSqlite().setDatabasePath(databasePath.toString());
        return properties;
    }
}
