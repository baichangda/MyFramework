package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.connection.MqttConnectionRegistry;
import cn.bcd.app.mqtt.server.connection.MqttKeepAliveManager;
import cn.bcd.app.mqtt.server.handler.MqttConnectHandler;
import cn.bcd.app.mqtt.server.handler.MqttDisconnectHandler;
import cn.bcd.app.mqtt.server.handler.MqttPingHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttConnectionControlTest {

    @Test
    void shouldRespondToPingAfterConnect() {
        EmbeddedChannel channel = connectedChannel(60);

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xc0, 0x00})));

        assertArrayEquals(new byte[]{(byte) 0xd0, 0x00}, readOutbound(channel));
        assertTrue(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldCloseNormallyOnDisconnect() {
        EmbeddedChannel channel = connectedChannel(60);
        MqttConnection connection = connection(channel);

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xe0, 0x00})));

        assertFalse(channel.isActive());
        assertEquals(MqttConnectionCloseReason.NORMAL_DISCONNECT,
                connection.closeReason());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldConfigureOneAndHalfKeepAliveAndCloseOnReaderIdle() {
        EmbeddedChannel channel = connectedChannel(2);
        MqttConnection connection = connection(channel);
        IdleStateHandler idleStateHandler = channel.pipeline().get(IdleStateHandler.class);

        assertEquals(3000, idleStateHandler.getReaderIdleTimeInMillis());
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        assertFalse(channel.isActive());
        assertEquals(MqttConnectionCloseReason.KEEP_ALIVE_TIMEOUT,
                connection.closeReason());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldDisableIdleTimeoutWhenKeepAliveIsZero() {
        EmbeddedChannel channel = connectedChannel(0);

        assertNull(channel.pipeline().get(IdleStateHandler.class));
        assertTrue(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldMarkUnclassifiedCloseAsNetworkClosed() {
        EmbeddedChannel channel = connectedChannel(60);
        MqttConnection connection = connection(channel);

        channel.close().syncUninterruptibly();

        assertEquals(MqttConnectionCloseReason.NETWORK_CLOSED,
                connection.closeReason());
        channel.finishAndReleaseAll();
    }

    private static EmbeddedChannel connectedChannel(int keepAliveSeconds) {
        EmbeddedChannel channel = newChannel();
        byte[] connectPacket = {
                0x10, 0x0f,
                0x00, 0x04, 'M', 'Q', 'T', 'T',
                0x04, 0x02,
                (byte) (keepAliveSeconds >>> 8), (byte) keepAliveSeconds,
                0x00, 0x03, 'c', 'i', 'd'
        };
        channel.writeInbound(Unpooled.wrappedBuffer(connectPacket));
        readOutbound(channel);
        return channel;
    }

    private static EmbeddedChannel newChannel() {
        MqttConnectionRegistry connectionRegistry = new MqttConnectionRegistry();
        EmbeddedChannel channel = new EmbeddedChannel();
        MqttConnection connection = new MqttConnection(channel);
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(new MqttPacketDispatcher(
                connection,
                connectionRegistry,
                new MqttConnectHandler(new MqttKeepAliveManager(), connectionRegistry),
                new MqttPingHandler(),
                new MqttDisconnectHandler()));
        return channel;
    }

    private static MqttConnection connection(EmbeddedChannel channel) {
        return channel.pipeline().get(MqttPacketDispatcher.class).connection();
    }

    private static byte[] readOutbound(EmbeddedChannel channel) {
        ByteBuf buffer = channel.readOutbound();
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }
}
