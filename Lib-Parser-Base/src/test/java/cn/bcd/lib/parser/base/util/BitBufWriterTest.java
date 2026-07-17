package cn.bcd.lib.parser.base.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BitBufWriterTest {

    @Test
    void writesEveryWidthAtEveryBitOffset() {
        final Random random = new Random(0xB17B_0FF5L);
        for (int start = 0; start < 8; start++) {
            for (int bit = 1; bit <= 64; bit++) {
                final long value = random.nextLong();
                final byte[] expected = new byte[(start + bit + 7) >>> 3];
                writeReference(expected, start, value, bit);

                final ByteBuf byteBuf = Unpooled.buffer(expected.length);
                final BitBuf_writer writer = new BitBuf_writer(byteBuf);
                writer.skip(start);
                writer.write(value, bit);
                assertState(writer, expected, start + bit);

                writer.finish();
                assertEquals(expected.length, byteBuf.writerIndex());
                assertArrayEquals(expected, bytes(byteBuf), "start=" + start + ", bit=" + bit);
                assertEquals(0, writer.bitOffset);
                assertEquals(0, writer.b);
            }
        }
    }

    @Test
    void preservesStateAcrossMixedWritesAndSkips() {
        final byte[] expected = new byte[512];
        final ByteBuf byteBuf = Unpooled.buffer(expected.length);
        final BitBuf_writer writer = new BitBuf_writer(byteBuf);
        final Random random = new Random(0x5EEDL);
        int position = 0;

        while (position < expected.length * 8 - 64) {
            if (random.nextBoolean()) {
                final int bit = 1 + random.nextInt(64);
                final long value = random.nextLong();
                writer.write(value, bit);
                writeReference(expected, position, value, bit);
                position += bit;
            } else {
                final int bit = random.nextInt(65);
                writer.skip(bit);
                position += bit;
            }
            assertState(writer, expected, position);
        }

        writer.finish();
        final int byteLength = (position + 7) >>> 3;
        assertEquals(byteLength, byteBuf.writerIndex());
        final byte[] actual = new byte[byteLength];
        byteBuf.getBytes(0, actual);
        final byte[] expectedResult = new byte[byteLength];
        System.arraycopy(expected, 0, expectedResult, 0, byteLength);
        assertArrayEquals(expectedResult, actual);
    }

    @Test
    @SuppressWarnings("deprecation")
    void preservesNetworkBitOrderForLittleEndianByteBufViews() {
        final ByteBuf byteBuf = Unpooled.buffer(16).order(ByteOrder.LITTLE_ENDIAN);
        final BitBuf_writer writer = new BitBuf_writer(byteBuf);
        writer.write(0x0123456789ABCDEFL, 64);
        writer.write(0x1357, 16);
        writer.finish();

        assertArrayEquals(new byte[]{
                0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
                0x13, 0x57
        }, bytes(byteBuf));
    }

    private static void writeReference(byte[] target, int position, long value, int bit) {
        final long masked = bit == 64 ? value : value & ((1L << bit) - 1);
        for (int i = 0; i < bit; i++) {
            final int targetBit = position + i;
            final int bitValue = (int) ((masked >>> (bit - 1 - i)) & 1);
            target[targetBit >>> 3] |= (byte) (bitValue << (7 - (targetBit & 7)));
        }
    }

    private static void assertState(BitBuf_writer writer, byte[] expected, int position) {
        assertEquals(position >>> 3, writer.byteBuf.writerIndex(), "writerIndex");
        assertEquals(position & 7, writer.bitOffset, "bitOffset");
        if ((position & 7) != 0) {
            assertEquals(expected[position >>> 3], writer.b, "cached byte");
        } else {
            assertEquals(0, writer.b, "cleared cached byte");
        }
    }

    private static byte[] bytes(ByteBuf byteBuf) {
        final byte[] result = new byte[byteBuf.writerIndex()];
        byteBuf.getBytes(0, result);
        return result;
    }
}
