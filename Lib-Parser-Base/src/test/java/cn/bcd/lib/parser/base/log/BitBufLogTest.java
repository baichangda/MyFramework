package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.util.*;
import io.netty.buffer.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BitBufLogTest {
    @Test
    void everyWidthAndOffsetPreserveValuesBytesAndLogs() {
        byte[] source = new byte[10];
        Random random = new Random(123);
        random.nextBytes(source);
        for (int start = 0; start < 8; start++) {
            for (int bit = 1; bit <= 64; bit++) {
                for (boolean unsigned : new boolean[]{false, true}) {
                    ByteBuf input = Unpooled.wrappedBuffer(source);
                    ByteBuf output = Unpooled.buffer();
                    try {
                        BitBuf_reader_log reader = new BitBuf_reader_log(input);
                        BitBuf_writer_log writer = new BitBuf_writer_log(output);
                        reader.skip(start);
                        if (start > 0) {
                            writer.write(-1, start);
                        }
                        reader.takeLogs();
                        writer.takeLogs();
                        long raw = reference(source, start, bit);
                        long expected = unsigned || bit == 64 ? raw : (raw << (64 - bit)) >> (64 - bit);
                        assertEquals(expected, reader.read(bit, unsigned));
                        BitBuf_reader_log.ReadLog readLog = (BitBuf_reader_log.ReadLog) reader.takeLogs()[0];
                        assertEquals(raw, readLog.val1);
                        assertEquals(expected, readLog.val2);
                        assertArrayEquals(Arrays.copyOf(source, (start + bit + 7) >>> 3), readLog.bytes);
                        assertEquals(start, readLog.bitStart);
                        assertEquals(bit, readLog.bit);
                        assertFalse(readLog.msg().isEmpty());
                        assertEquals((start + bit + 7) >>> 3, input.readerIndex());
                        assertEquals((start + bit) & 7, reader.bitOffset);
                        assertEquals(reference(source, start + bit, 3), reader.read(3, true));

                        long value = random.nextLong();
                        byte[] bytes = new byte[(start + bit + 7) >>> 3];
                        for (int i = 0; i < start + bit; i++) {
                            int digit = i < start ? 1 : (int) ((value >>> (bit - 1 - (i - start))) & 1);
                            bytes[i >>> 3] |= (byte) (digit << (7 - (i & 7)));
                        }
                        writer.write(value, bit);
                        BitBuf_writer_log.WriteLog writeLog = (BitBuf_writer_log.WriteLog) writer.takeLogs()[0];
                        assertArrayEquals(bytes, writeLog.bytes);
                        assertEquals(value & BitBuf_writer.mask(bit), writeLog.val1);
                        assertEquals(start, writeLog.bitStart);
                        assertEquals(bit, writeLog.bit);
                        assertFalse(writeLog.msg().isEmpty());
                        assertEquals((start + bit) >>> 3, output.writerIndex());
                        assertEquals((start + bit) & 7, writer.bitOffset);
                        writer.finish();
                        byte[] actual = new byte[output.readableBytes()];
                        output.getBytes(0, actual);
                        assertArrayEquals(bytes, actual);
                    } finally {
                        input.release();
                        output.release();
                    }
                }
            }
        }
    }

    @Test
    void skipsPreserveBytesAndState() {
        byte[] source = new byte[20];
        new Random(456).nextBytes(source);
        for (int start = 0; start < 8; start++) {
            for (int skip : new int[]{0, 1, 7, 8, 9, 63, 64, 65}) {
                ByteBuf input = Unpooled.wrappedBuffer(source);
                ByteBuf output = Unpooled.buffer();
                try {
                    BitBuf_reader_log reader = new BitBuf_reader_log(input);
                    BitBuf_writer_log writer = new BitBuf_writer_log(output);
                    reader.skip(start);
                    if (start > 0) {
                        writer.write(-1, start);
                    }
                    reader.takeLogs();
                    writer.takeLogs();
                    reader.skip(skip);
                    writer.skip(skip);
                    assertEquals((start + skip + 7) >>> 3, input.readerIndex());
                    assertEquals((start + skip) >>> 3, output.writerIndex());
                    assertEquals((start + skip) & 7, reader.bitOffset);
                    assertEquals(reader.bitOffset, writer.bitOffset);
                    if (skip == 0) {
                        assertEquals(0, reader.takeLogs().length);
                        assertEquals(0, writer.takeLogs().length);
                    } else {
                        int length = (start + skip + 7) >>> 3;
                        assertArrayEquals(Arrays.copyOf(source, length), reader.takeLogs()[0].bytes);
                        byte[] expected = new byte[length];
                        expected[0] = (byte) (0xFF << (8 - start));
                        assertArrayEquals(expected, writer.takeLogs()[0].bytes);
                    }
                    assertEquals(reference(source, start + skip, 3), reader.read(3, true));
                } finally {
                    input.release();
                    output.release();
                }
            }
        }
    }

    @Test
    void invalidArgumentsDoNotChangeState() {
        for (boolean logged : new boolean[]{false, true}) {
            for (int start : new int[]{0, 3}) {
                ByteBuf input = Unpooled.wrappedBuffer(new byte[]{(byte) 0xAB, 0x12});
                ByteBuf output = Unpooled.buffer();
                try {
                    BitBuf_reader reader = logged ? new BitBuf_reader_log(input) : new BitBuf_reader(input);
                    BitBuf_writer writer = logged ? new BitBuf_writer_log(output) : new BitBuf_writer(output);
                    if (start > 0) {
                        reader.read(start, true);
                        writer.write(5, start);
                    }
                    if (logged) {
                        ((BitBuf_reader_log) reader).takeLogs();
                        ((BitBuf_writer_log) writer).takeLogs();
                    }
                    byte readByte = reader.b;
                    byte writeByte = writer.b;
                    for (int bit : new int[]{Integer.MIN_VALUE, -1, 0, 65, Integer.MAX_VALUE}) {
                        assertThrows(IllegalArgumentException.class, () -> reader.read(bit, true));
                        assertThrows(IllegalArgumentException.class, () -> writer.write(123, bit));
                    }
                    assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
                    assertThrows(IllegalArgumentException.class, () -> writer.skip(-1));
                    reader.skip(0);
                    writer.skip(0);
                    assertEquals((start + 7) >>> 3, input.readerIndex());
                    assertEquals(0, output.writerIndex());
                    assertEquals(start, reader.bitOffset);
                    assertEquals(start, writer.bitOffset);
                    assertEquals(readByte, reader.b);
                    assertEquals(writeByte, writer.b);
                    if (logged) {
                        assertEquals(0, ((BitBuf_reader_log) reader).takeLogs().length);
                        assertEquals(0, ((BitBuf_writer_log) writer).takeLogs().length);
                    }
                } finally {
                    input.release();
                    output.release();
                }
            }
        }
    }

    private static long reference(byte[] source, int position, int bit) {
        long value = 0;
        for (int i = 0; i < bit; i++) {
            int index = position + i;
            value = (value << 1) | ((source[index >>> 3] >>> (7 - (index & 7))) & 1);
        }
        return value;
    }
}
