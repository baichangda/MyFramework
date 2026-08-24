package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.handler.MqttConnectHandler;
import cn.bcd.app.mqtt.server.handler.MqttDisconnectHandler;
import cn.bcd.app.mqtt.server.handler.MqttPingHandler;
import cn.bcd.app.mqtt.server.protocol.MqttPacketDispatcher;
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
        MqttConnectionRegistry registry = new MqttConnectionRegistry();
        TestClient first = connect(registry);
        MqttConnectionRegistration firstRegistration = first.connection().registration();

        TestClient second = connect(registry);
        first.channel().runPendingTasks();
        MqttConnectionRegistration secondRegistration = second.connection().registration();

        assertFalse(first.channel().isActive());
        assertEquals(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER,
                first.connection().closeReason());
        assertTrue(second.channel().isActive());
        assertTrue(secondRegistration.generation() > firstRegistration.generation());
        assertSame(second.connection(), registry.findConnection("cid").orElseThrow());
        assertEquals(1, registry.size());

        second.channel().close().syncUninterruptibly();
        assertTrue(registry.findConnection("cid").isEmpty());
        assertEquals(0, registry.size());
        first.channel().finishAndReleaseAll();
        second.channel().finishAndReleaseAll();
    }

    @Test
    void shouldNotRemoveNewRegistrationWhenOldConnectionUnregistersLate() {
        MqttConnectionRegistry registry = new MqttConnectionRegistry();
        MqttConnection first = new MqttConnection(new EmbeddedChannel());
        MqttConnection second = new MqttConnection(new EmbeddedChannel());

        registry.register("cid", first);
        registry.register("cid", second);
        ((EmbeddedChannel) first.channel()).runPendingTasks();
        registry.unregister(first);

        assertSame(second, registry.findConnection("cid").orElseThrow());
        assertEquals(1, registry.size());

        registry.unregister(second);
        first.channel().close().syncUninterruptibly();
        second.channel().close().syncUninterruptibly();
    }

    private static TestClient connect(MqttConnectionRegistry registry) {
        EmbeddedChannel channel = new EmbeddedChannel();
        MqttConnection connection = new MqttConnection(channel);
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(new MqttPacketDispatcher(
                connection,
                registry,
                new MqttConnectHandler(new MqttKeepAliveManager(), registry),
                new MqttPingHandler(),
                new MqttDisconnectHandler()));
        channel.writeInbound(Unpooled.wrappedBuffer(CONNECT));
        ByteBuf connAck = channel.readOutbound();
        connAck.release();
        return new TestClient(channel, connection);
    }

    private record TestClient(EmbeddedChannel channel, MqttConnection connection) {
    }
}
