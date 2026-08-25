package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.connection.MqttConnectionContext;
import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.support.MqttTestBroker;
import cn.bcd.app.mqtt.server.support.MqttTestChannel;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttConnectTest {

    private static final byte[] VALID_CONNECT = {
            0x10, 0x0f,
            0x00, 0x04, 'M', 'Q', 'T', 'T',
            0x04, 0x02, 0x00, 0x3c,
            0x00, 0x03, 'c', 'i', 'd'
    };

    @Test
    void shouldAcceptValidMqtt311Connect() {
        EmbeddedChannel channel = newChannel();

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(VALID_CONNECT)));

        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x00}, readOutbound(channel));
        MqttConnectionContext context = MqttTestChannel.connection(channel).context();
        assertNotNull(context);
        assertEquals("cid", context.clientId());
        assertTrue(context.cleanSession());
        assertEquals(60, context.keepAliveSeconds());
        assertTrue(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldRejectEmptyClientIdentifier() {
        byte[] emptyClientId = {
                0x10, 0x0c,
                0x00, 0x04, 'M', 'Q', 'T', 'T',
                0x04, 0x02, 0x00, 0x3c,
                0x00, 0x00
        };
        EmbeddedChannel channel = newChannel();

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(emptyClientId)));

        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x02}, readOutbound(channel));
        assertFalse(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldRejectMqtt31() {
        byte[] mqtt31Connect = {
                0x10, 0x11,
                0x00, 0x06, 'M', 'Q', 'I', 's', 'd', 'p',
                0x03, 0x02, 0x00, 0x3c,
                0x00, 0x03, 'c', 'i', 'd'
        };
        EmbeddedChannel channel = newChannel();

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(mqtt31Connect)));

        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x01}, readOutbound(channel));
        assertFalse(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldRejectMqtt5WithMqtt5ConnAck() {
        byte[] mqtt5Connect = {
                0x10, 0x10,
                0x00, 0x04, 'M', 'Q', 'T', 'T',
                0x05, 0x02, 0x00, 0x3c,
                0x00,
                0x00, 0x03, 'c', 'i', 'd'
        };
        EmbeddedChannel channel = newChannel();

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(mqtt5Connect)));

        assertArrayEquals(new byte[]{0x20, 0x03, 0x00, (byte) 0x84, 0x00}, readOutbound(channel));
        assertFalse(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldCloseWhenFirstPacketIsNotConnect() {
        EmbeddedChannel channel = newChannel();

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xc0, 0x00})));

        assertFalse(channel.isActive());
        assertTrue(channel.outboundMessages().isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldCloseOnSecondConnect() {
        EmbeddedChannel channel = newChannel();
        channel.writeInbound(Unpooled.wrappedBuffer(VALID_CONNECT));
        readOutbound(channel);

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(VALID_CONNECT)));

        assertFalse(channel.isActive());
        assertTrue(channel.outboundMessages().isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldRefuseNewClientIdWhenCapacityIsExhausted() {
        MqttServerProperties properties = new MqttServerProperties();
        properties.getLimits().setClientIds(1);
        MqttBroker broker = MqttTestBroker.create(properties);
        EmbeddedChannel first = MqttTestChannel.open(broker);
        first.writeInbound(Unpooled.wrappedBuffer(
                MqttTestChannel.connectPacket("first", false)));
        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x00}, readOutbound(first));

        EmbeddedChannel second = MqttTestChannel.open(broker);
        second.writeInbound(Unpooled.wrappedBuffer(
                MqttTestChannel.connectPacket("second", false)));

        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x03}, readOutbound(second));
        assertFalse(second.isActive());
        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
    }

    private static EmbeddedChannel newChannel() {
        return MqttTestChannel.open();
    }

    private static byte[] readOutbound(EmbeddedChannel channel) {
        return MqttTestChannel.readOutbound(channel);
    }
}
