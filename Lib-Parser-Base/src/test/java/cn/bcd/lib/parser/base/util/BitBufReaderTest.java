package cn.bcd.lib.parser.base.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BitBufReaderTest {

    @Test
    void readsEveryWidthAtEveryBitOffset() {
        final byte[] source = {
                (byte) 0x81, 0x72, 0x40, (byte) 0xFE,
                0x35, (byte) 0xA9, 0x16, (byte) 0xC3,
                0x6D, (byte) 0x88, 0x5A, (byte) 0xF1
        };

        for (int start = 0; start < 8; start++) {
            for (int bit = 1; bit <= 64; bit++) {
                for (boolean unsigned : new boolean[]{true, false}) {
                    final ByteBuf byteBuf = Unpooled.wrappedBuffer(source);
                    final BitBuf_reader reader = new BitBuf_reader(byteBuf);
                    reader.skip(start);

                    final long raw = readReference(source, start, bit);
                    assertEquals(BitBuf_reader.valueOf(raw, bit, unsigned), reader.read(bit, unsigned),
                            "start=" + start + ", bit=" + bit + ", unsigned=" + unsigned);
                    assertState(reader, source, start + bit);
                }
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void preservesBitOrderForLittleEndianByteBufViews() {
        final byte[] source = {
                (byte) 0x81, 0x72, 0x40, (byte) 0xFE,
                0x35, (byte) 0xA9, 0x16, (byte) 0xC3,
                0x6D, (byte) 0x88
        };

        for (int start = 0; start < 8; start++) {
            for (int bit = 1; bit <= 64; bit++) {
                final ByteBuf byteBuf = Unpooled.wrappedBuffer(source).order(ByteOrder.LITTLE_ENDIAN);
                final BitBuf_reader reader = new BitBuf_reader(byteBuf);
                reader.skip(start);

                final long raw = readReference(source, start, bit);
                assertEquals(raw, reader.read(bit, true), "start=" + start + ", bit=" + bit);
                assertState(reader, source, start + bit);
            }
        }
    }

    @Test
    void preservesStateAcrossMixedReadsAndSkips() {
        final byte[] source = new byte[256];
        new Random(0xB17B_0FF5L).nextBytes(source);
        final ByteBuf byteBuf = Unpooled.wrappedBuffer(source);
        final BitBuf_reader reader = new BitBuf_reader(byteBuf);
        final Random random = new Random(0x5EEDL);
        int position = 0;

        while (position < source.length * 8 - 64) {
            if (random.nextBoolean()) {
                final int bit = 1 + random.nextInt(64);
                final boolean unsigned = random.nextBoolean();
                final long raw = readReference(source, position, bit);
                assertEquals(BitBuf_reader.valueOf(raw, bit, unsigned), reader.read(bit, unsigned));
                position += bit;
            } else {
                final int bit = random.nextInt(65);
                reader.skip(bit);
                position += bit;
            }
            assertState(reader, source, position);
        }
    }

    private static long readReference(byte[] source, int position, int bit) {
        long value = 0;
        for (int i = 0; i < bit; i++) {
            final int absoluteBit = position + i;
            value = (value << 1)
                    | ((source[absoluteBit >>> 3] >>> (7 - (absoluteBit & 7))) & 1);
        }
        return value;
    }

    private static void assertState(BitBuf_reader reader, byte[] source, int position) {
        assertEquals((position + 7) >>> 3, reader.byteBuf.readerIndex(), "readerIndex");
        assertEquals(position & 7, reader.bitOffset, "bitOffset");
        if ((position & 7) != 0) {
            assertEquals(source[position >>> 3], reader.b, "cached byte");
        }
    }
}
