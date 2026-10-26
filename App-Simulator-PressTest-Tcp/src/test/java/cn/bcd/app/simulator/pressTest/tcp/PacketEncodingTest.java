package cn.bcd.app.simulator.pressTest.tcp;

import cn.bcd.app.simulator.pressTest.tcp.v2016.Starter_v2016;
import cn.bcd.app.simulator.pressTest.tcp.v2025.Starter_v2025;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PacketEncodingTest {
    private static final String VIN = "TEST0000000000123";
    private static final long TS = Instant.parse("2026-10-26T00:00:00Z").toEpochMilli();

    @Test
    void v2016RecomputesChecksumAfterChangingVinAndTime() {
        Encoder2016 encoder = new Encoder2016();
        ByteBuf first = encoder.encode(VIN, TS);
        ByteBuf second = encoder.encode(VIN, TS + 1000);
        try {
            assertPacket(first);
            assertPacket(second);
            assertNotEquals(first.getUnsignedByte(first.writerIndex() - 1),
                    second.getUnsignedByte(second.writerIndex() - 1));
            var packet = cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet.read(first.duplicate());
            assertEquals(new Date(TS),
                    ((cn.bcd.lib.parser.protocol.gb32960.v2016.data.VehicleRunData) packet.data).collectTime);
        } finally {
            first.release();
            second.release();
        }
    }

    @Test
    void v2025RecomputesChecksumAfterChangingVinAndTime() {
        Encoder2025 encoder = new Encoder2025();
        ByteBuf first = encoder.encode(VIN, TS);
        ByteBuf second = encoder.encode(VIN, TS + 1000);
        try {
            assertPacket(first);
            assertPacket(second);
            assertNotEquals(first.getUnsignedByte(first.writerIndex() - 1),
                    second.getUnsignedByte(second.writerIndex() - 1));
            var packet = cn.bcd.lib.parser.protocol.gb32960.v2025.data.Packet.read(first.duplicate());
            assertEquals(new Date(TS),
                    ((cn.bcd.lib.parser.protocol.gb32960.v2025.data.VehicleRunData) packet.data).collectTime);
        } finally {
            first.release();
            second.release();
        }
    }

    private static void assertPacket(ByteBuf buffer) {
        assertEquals(VIN, buffer.getCharSequence(4, 17, StandardCharsets.US_ASCII).toString());
        assertEquals(buffer.readableBytes() - 25, buffer.getUnsignedShort(22));
        int xor = 0;
        for (int i = 2; i < buffer.writerIndex() - 1; i++) {
            xor ^= buffer.getUnsignedByte(i);
        }
        assertEquals(xor, buffer.getUnsignedByte(buffer.writerIndex() - 1));
    }

    private static class Encoder2016 extends Starter_v2016 {
        ByteBuf encode(String vin, long ts) {
            return toByteBuf(initSample(vin), ts);
        }
    }

    private static class Encoder2025 extends Starter_v2025 {
        ByteBuf encode(String vin, long ts) {
            return toByteBuf(initSample(vin), ts);
        }
    }
}
