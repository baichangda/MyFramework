package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttSessionTest {

    @Test
    void shouldResumePersistentSessionAndSetSessionPresent() {
        MqttBroker broker = new MqttBroker();

        TestClient first = connect(false, broker);
        MqttSession firstSession = first.connection().session();
        assertEquals(0, first.connAck()[2]);
        first.channel().close().syncUninterruptibly();
        assertSame(firstSession, broker.findSession("cid").orElseThrow());

        TestClient second = connect(false, broker);
        assertEquals(1, second.connAck()[2]);
        assertSame(firstSession, second.connection().session());

        second.channel().close().syncUninterruptibly();
        first.channel().finishAndReleaseAll();
        second.channel().finishAndReleaseAll();
    }

    @Test
    void shouldDiscardPreviousSessionWhenCleanSessionIsTrue() {
        MqttBroker broker = new MqttBroker();
        TestClient persistent = connect(false, broker);
        MqttSession previousSession = persistent.connection().session();

        TestClient clean = connect(true, broker);
        persistent.channel().runPendingTasks();

        assertEquals(0, clean.connAck()[2]);
        assertNotSame(previousSession, clean.connection().session());
        assertSame(clean.connection().session(), broker.findSession("cid").orElseThrow());

        clean.channel().close().syncUninterruptibly();
        assertTrue(broker.findSession("cid").isEmpty());
        persistent.channel().finishAndReleaseAll();
        clean.channel().finishAndReleaseAll();
    }

    @Test
    void shouldRemoveCleanSessionWhenConnectionCloses() {
        MqttBroker broker = new MqttBroker();
        TestClient client = connect(true, broker);

        client.channel().close().syncUninterruptibly();

        assertEquals(0, broker.size());
        client.channel().finishAndReleaseAll();
    }

    private static TestClient connect(
            boolean cleanSession,
            MqttBroker broker) {
        EmbeddedChannel channel = new EmbeddedChannel();
        MqttConnection connection = new MqttConnection(broker);
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(connection);
        channel.writeInbound(Unpooled.wrappedBuffer(connectPacket(cleanSession)));
        return new TestClient(channel, connection, readOutbound(channel));
    }

    private static byte[] connectPacket(boolean cleanSession) {
        return new byte[]{
                0x10, 0x0f,
                0x00, 0x04, 'M', 'Q', 'T', 'T',
                0x04, cleanSession ? (byte) 0x02 : 0x00, 0x00, 0x3c,
                0x00, 0x03, 'c', 'i', 'd'
        };
    }

    private static byte[] readOutbound(EmbeddedChannel channel) {
        ByteBuf buffer = channel.readOutbound();
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }

    private record TestClient(
            EmbeddedChannel channel,
            MqttConnection connection,
            byte[] connAck
    ) {
    }
}
