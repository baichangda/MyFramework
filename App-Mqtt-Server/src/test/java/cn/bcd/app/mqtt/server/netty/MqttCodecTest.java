package cn.bcd.app.mqtt.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttCodecTest {

    @Test
    void shouldDecodeFragmentedMqtt311ConnectPacket() {
        byte[] connectPacket = {
                0x10, 0x0f,
                0x00, 0x04, 'M', 'Q', 'T', 'T',
                0x04, 0x02, 0x00, 0x3c,
                0x00, 0x03, 'c', 'i', 'd'
        };
        EmbeddedChannel channel = new EmbeddedChannel(new MqttDecoder(1024, 64, true));

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(connectPacket, 0, 5)));
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(connectPacket, 5, connectPacket.length - 5)));

        MqttConnectMessage message = channel.readInbound();
        assertTrue(message.decoderResult().isSuccess());
        assertEquals("cid", message.payload().clientIdentifier());
        assertFalse(channel.finish());
    }

    @Test
    void shouldEncodePingResponse() {
        EmbeddedChannel channel = new EmbeddedChannel(MqttEncoder.INSTANCE);

        assertTrue(channel.writeOutbound(MqttMessage.PINGRESP));

        ByteBuf encoded = channel.readOutbound();
        try {
            assertEquals(0xd0, encoded.readUnsignedByte());
            assertEquals(0, encoded.readUnsignedByte());
            assertFalse(encoded.isReadable());
        } finally {
            encoded.release();
        }
        assertFalse(channel.finish());
    }
}
