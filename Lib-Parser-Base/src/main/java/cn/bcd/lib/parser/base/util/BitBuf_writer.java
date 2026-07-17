package cn.bcd.lib.parser.base.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.nio.ByteOrder;

public class BitBuf_writer {
    public final ByteBuf byteBuf;

    public byte b;

    //当前写入字节bit偏移量
    public int bitOffset = 0;

    public BitBuf_writer(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
    }

    public static void main(String[] args) {
        final long t1 = System.currentTimeMillis();
        for (int i = 0; i < 1; i++) {
//            final ByteBuf bb = Unpooled.buffer();
//            final BitBuf_writer bitBufWriter = BitBuf_writer.newBitBuf(bb);
//            bitBufWriter.write(1L, 1);
//            bitBufWriter.write(28900L, 15);
//            bitBufWriter.write(1L, 1);
//            bitBufWriter.write(28900L, 15);
//            bitBufWriter.write(1L, 1);
//            bitBufWriter.write(28900L, 15);
//            bitBufWriter.write(1L, 1);
//            bitBufWriter.write(28900L, 15);
//            bitBufWriter.write(1L, 1);
//            bitBufWriter.write(28900L, 15);
//            bitBufWriter.write(1L, 1);
//            bitBufWriter.write(28900L, 15);
//            bitBufWriter.write(1L, 1);
//            bitBufWriter.write(28900L, 15);
//            bitBufWriter.write(1L, 1);
//            bitBufWriter.write(28900L, 15);
//            bitBufWriter.write(1L, 1);
//            bitBufWriter.write(28900L, 15);
//            bitBufWriter.write(1L, 1);
//            bitBufWriter.write(28900L, 15);
//            bitBufWriter.write(28900L, 15);

//            System.out.println(ByteBufUtil.hexDump(bb));


            final ByteBuf bb = Unpooled.buffer();
            final BitBuf_writer bitBufWriter = new BitBuf_writer(bb);
            bitBufWriter.write(4, 3);
            bitBufWriter.write(0, 3);
            bitBufWriter.skip(3);
            bitBufWriter.write(-217, 9);
            bitBufWriter.finish();
            System.out.println(ByteBufUtil.hexDump(bb));
        }
        System.out.println(System.currentTimeMillis() - t1);
    }

    /**
     * 获取指定位宽的掩码
     * 处理 bit=64 的特殊情况（Java 中 1L << 64 等同于 1L << 0）
     */
    public static long mask(int bit) {
        return bit == 64 ? -1L : ((1L << bit) - 1);
    }

    @SuppressWarnings("deprecation")
    public void write(long l, int bit) {
        l = l & mask(bit);
        final ByteBuf byteBuf = this.byteBuf;
        final int bitOffset = this.bitOffset;
        if (bit + bitOffset > 64) {
            writeOverEightBytes(l, bit, bitOffset);
            return;
        }
        byte b = this.b;
        final int temp = bit + bitOffset;
        final int finalBitOffset = temp & 7;
        final long newL;
        final int byteLen;
        if (finalBitOffset == 0) {
            byteLen = temp >> 3;
            newL = l;
        } else {
            byteLen = (temp >> 3) + 1;
            newL = l << (8 - finalBitOffset);
        }
        b |= (byte) (newL >> ((byteLen - 1) << 3));
        for (int i = 1; i < byteLen; i++) {
            byteBuf.writeByte(b);
            b = (byte) (newL >> ((byteLen - i - 1) << 3));
        }
        if (finalBitOffset == 0) {
            byteBuf.writeByte(b);
            this.bitOffset = 0;
            this.b = 0;
        } else {
            this.bitOffset = finalBitOffset;
            this.b = b;
        }
    }

    private void writeOverEightBytes(long value, int bit, int bitOffset) {
        final ByteBuf byteBuf = this.byteBuf;
        final int available = 8 - bitOffset;
        byteBuf.writeByte(b | (byte) (value >>> (bit - available)));

        int remaining = bit - available;
        while (remaining >= 8) {
            remaining -= 8;
            byteBuf.writeByte((byte) (value >>> remaining));
        }
        if (remaining == 0) {
            b = 0;
            this.bitOffset = 0;
        } else {
            b = (byte) (value << (8 - remaining));
            this.bitOffset = remaining;
        }
    }

    public void skip(int bit) {
        final ByteBuf byteBuf = this.byteBuf;
        final int bitOffset = this.bitOffset;
        byte b = this.b;
        final int temp = bit + bitOffset;
        final boolean newBitOffsetZero = (temp & 7) == 0;
        final int byteLen = (temp >> 3) + (newBitOffsetZero ? 0 : 1);
        if (byteLen == 1) {
            if (newBitOffsetZero) {
                byteBuf.writeByte(b);
                b = 0;
            }
        } else {
            if (bitOffset == 0) {
                if (newBitOffsetZero) {
                    byteBuf.writeZero(byteLen);
                } else {
                    byteBuf.writeZero(byteLen - 1);
                }

            } else {
                byteBuf.writeByte(b);
                if (newBitOffsetZero) {
                    byteBuf.writeZero(byteLen - 1);
                } else {
                    byteBuf.writeZero(byteLen - 2);
                }
            }
            b = 0;
        }
        this.bitOffset = temp & 7;
        this.b = b;
    }

    public void finish() {
        if (bitOffset > 0) {
            byteBuf.writeByte(b);
        }
        b = 0;
        bitOffset = 0;
    }
}
