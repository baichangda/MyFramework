package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttConnectionTakeoverTest {

    private static final byte[] CONNECT = {
            0x10, 0x0f,
            0x00, 0x04, 'M', 'Q', 'T', 'T',
            0x04, 0x02, 0x00, 0x3c,
            0x00, 0x03, 'c', 'i', 'd'
    };

    @Test
    void shouldTakeOverExistingConnectionWithSameClientId() {
        MqttBroker broker = new MqttBroker();
        TestClient first = connect(broker);
        TestClient second = connect(broker);
        first.channel().runPendingTasks();

        assertFalse(first.channel().isActive());
        assertEquals(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER,
                first.connection().closeReason());
        assertTrue(second.channel().isActive());
        assertSame(second.connection(), broker.findConnection("cid").orElseThrow());
        assertEquals(1, broker.size());

        second.channel().close().syncUninterruptibly();
        assertTrue(broker.findConnection("cid").isEmpty());
        assertEquals(0, broker.size());
        first.channel().finishAndReleaseAll();
        second.channel().finishAndReleaseAll();
    }

    @Test
    void shouldNotRemoveNewConnectionWhenOldConnectionClosesLate() {
        MqttBroker broker = new MqttBroker();
        TestClient first = connect(broker);
        TestClient second = connect(broker);

        broker.disconnect(first.connection());

        assertSame(second.connection(), broker.findConnection("cid").orElseThrow());
        assertEquals(1, broker.size());

        first.channel().runPendingTasks();
        second.channel().close().syncUninterruptibly();
        first.channel().finishAndReleaseAll();
        second.channel().finishAndReleaseAll();
    }

    private static TestClient connect(MqttBroker broker) {
        EmbeddedChannel channel = new EmbeddedChannel();
        MqttConnection connection = new MqttConnection(broker);
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(connection);
        channel.writeInbound(Unpooled.wrappedBuffer(CONNECT));
        ByteBuf connAck = channel.readOutbound();
        connAck.release();
        return new TestClient(channel, connection);
    }

    private record TestClient(EmbeddedChannel channel, MqttConnection connection) {
    }
}
