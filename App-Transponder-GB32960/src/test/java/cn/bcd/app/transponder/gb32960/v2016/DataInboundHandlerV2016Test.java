package cn.bcd.app.transponder.gb32960.v2016;

import cn.bcd.app.transponder.gb32960.Monitor;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DataInboundHandlerV2016Test {
    private static final String VIN = "12345678901234567";

    @AfterEach
    void clearMetrics() {
        Monitor.clientMetrics.clear();
    }

    @Test
    void shouldReplySuccessWithoutDoubleReleasingInput() {
        EmbeddedChannel channel = new EmbeddedChannel(new DataInboundHandler_v2016());
        ByteBuf request = PacketUtil.build_byteBuf_timeData(
                VIN, PacketFlag.heartbeat, 0xFE, new Date());

        assertFalse(channel.writeInbound(request));

        ByteBuf response = channel.readOutbound();
        assertNotNull(response);
        assertEquals(0x01, response.getUnsignedByte(3));
        assertEquals(0, request.refCnt());
        response.release();
        channel.finishAndReleaseAll();
    }

}
