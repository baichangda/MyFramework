package cn.bcd.lib.parser.base.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class BitBuf_reader {

    public final ByteBuf byteBuf;
    public byte b;
    //当前readIndex的bit偏移量
    public int bitOffset = 0;

    public BitBuf_reader(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
    }

    public static void main(String[] args) {
        final long t1 = System.currentTimeMillis();
        final byte[] source = {
                (byte) 0xF0, (byte) 0xe4,
                (byte) 0xF0, (byte) 0xe4,
                (byte) 0xF0, (byte) 0xe4,
                (byte) 0xF0, (byte) 0xe4,
                (byte) 0xF0, (byte) 0xe4,
                (byte) 0xF0, (byte) 0xe4,
                (byte) 0xF0, (byte) 0xe4
        };
        for (int i = 0; i < 1; i++) {
//            ByteBuf bb = Unpooled.wrappedBuffer(source);
//            BitBuf_reader bitBuf = BitBuf_reader.newBitBuf(bb);
//            final long bitVal1 = bitBuf.read(1);
//            final long bitVal2 = bitBuf.read(15);
//            final long bitVal3 = bitBuf.read(1);
//            final long bitVal4 = bitBuf.read(15);
//            final long bitVal5 = bitBuf.read(1);
//            final long bitVal6 = bitBuf.read(15);
//            final long bitVal7 = bitBuf.read(1);
//            final long bitVal8 = bitBuf.read(15);
//            final long bitVal9 = bitBuf.read(1);
//            final long bitVal10 = bitBuf.read(15);
//            final long bitVal11 = bitBuf.read(1);
//            final long bitVal12 = bitBuf.read(15);
//            final long bitVal13 = bitBuf.read(15);
//            System.out.println(bitVal1);
//            System.out.println(bitVal2);
//            System.out.println(bitVal3);
//            System.out.println(bitVal4);
//            System.out.println(bitVal9);
//            System.out.println(bitVal10);
//            System.out.println(bitVal11);
//            System.out.println(bitVal12);
//            System.out.println(bitVal13);


            final byte[] source2 = {
                    (byte) 0x81, (byte) 0x72, (byte) 0x40,
            };
            ByteBuf bb2 = Unpooled.wrappedBuffer(source2);
            BitBuf_reader bitBuf2 = new BitBuf_reader(bb2);
            final long res1 = bitBuf2.read(3, true);
            final long res2 = bitBuf2.read(8, true);
            bitBuf2.skip(3);
            final long res3 = bitBuf2.read(9,  false);
            System.out.println(res1);
            System.out.println(res2);
            System.out.println(res3);
        }
        System.out.println(System.currentTimeMillis() - t1);

    }

    public long read(int bit, boolean unsigned) {
        final ByteBuf byteBuf = this.byteBuf;
        final int bitOffset = this.bitOffset;
        byte b;
        if (bitOffset == 0) {
            b = byteBuf.readByte();
        } else {
            b = this.b;
        }

        final int temp = bit + bitOffset;
        final int finalBitOffset = temp & 7;
        final int byteLen;
        long l;
        if (finalBitOffset == 0) {
            byteLen = temp >> 3;
            l = (b & 0xFFL) << (temp - 8);
        } else {
            byteLen = (temp >> 3) + 1;
            l = (b & 0xFFL) << (temp - finalBitOffset);
        }
        for (int i = 1; i < byteLen; i++) {
            b = byteBuf.readByte();
            l |= ((b & 0xFFL) << ((byteLen - 1 - i) << 3));
        }
        this.bitOffset = finalBitOffset;
        this.b = b;

        final long cRight = l >>> ((byteLen << 3) - bitOffset - bit);

        if (!unsigned && ((cRight >> (bit - 1)) & 1) == 1) {
            return cRight | (-1L << bit);
        } else {
            return cRight & ((1L << bit) - 1);
        }
    }

    public void skip(int bit) {
        final ByteBuf byteBuf = this.byteBuf;
        final int bitOffset = this.bitOffset;
        byte b = this.b;

        final int temp = bit + bitOffset;
        final int finalBitOffset = temp & 7;
        final boolean newBitOffsetZero = finalBitOffset == 0;
        final int byteLen = (temp >> 3) + (newBitOffsetZero ? 0 : 1);
        if (byteLen == 1) {
            if (bitOffset == 0) {
                b = byteBuf.readByte();
            }
        } else {
            if (bitOffset == 0) {
                if (newBitOffsetZero) {
                    byteBuf.skipBytes(byteLen);
                } else {
                    byteBuf.skipBytes(byteLen - 1);
                    b = byteBuf.readByte();
                }
            } else {
                if (newBitOffsetZero) {
                    byteBuf.skipBytes(byteLen - 1);
                } else {
                    byteBuf.skipBytes(byteLen - 2);
                    b = byteBuf.readByte();
                }
            }
        }
        this.bitOffset = finalBitOffset;
        this.b = b;
    }

    public void finish() {
        b = 0;
        bitOffset = 0;
    }

}
