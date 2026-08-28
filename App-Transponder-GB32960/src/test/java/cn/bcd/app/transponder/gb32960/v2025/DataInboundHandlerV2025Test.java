package cn.bcd.app.transponder.gb32960.v2025;

import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataInboundHandlerV2025Test {
    private static final String VIN = "12345678901234567";

    @Test
    void shouldUse2025ProtocolAndReplySuccess() {
        EmbeddedChannel channel = new EmbeddedChannel(new DataInboundHandler_v2025());
        ByteBuf request = PacketUtil.build_byteBuf_timeData(
                VIN, PacketFlag.heartbeat, 0xFE, new Date());

        assertFalse(channel.writeInbound(request));

        ByteBuf response = channel.readOutbound();
        assertNotNull(response);
        assertEquals(0x24, response.getUnsignedByte(0));
        assertEquals(0x24, response.getUnsignedByte(1));
        assertEquals(0x01, response.getUnsignedByte(3));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldIgnoreResponseFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new DataInboundHandler_v2025());
        ByteBuf responseFrame = PacketUtil.build_byteBuf_timeData(
                VIN, PacketFlag.heartbeat, 0x01, new Date());

        assertFalse(channel.writeInbound(responseFrame));
        assertNull(channel.readOutbound());
        channel.finishAndReleaseAll();
    }
}
