package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.util.BitBuf_writer;

import cn.bcd.lib.base.util.StringUtil;
import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

public final class BitBuf_writer_log extends BitBuf_writer {

    public List<Log> logs = new ArrayList<>();

    public BitBuf_writer_log(ByteBuf byteBuf) {
        super(byteBuf);
    }

    public static void main(String[] args) {
        final long t1 = System.currentTimeMillis();
        for (int i = 0; i < 1; i++) {

            final ByteBuf bb = Unpooled.buffer();
            final BitBuf_writer_log bitBufWriter = new BitBuf_writer_log(bb);
            bitBufWriter.write(4, 3);
            bitBufWriter.write(0, 3);
            bitBufWriter.skip(3);
            bitBufWriter.write(-217, 9);
            bitBufWriter.finish();
//            System.out.println(ByteBufUtil.hexDump(bb));
        }
        System.out.println(System.currentTimeMillis() - t1);
    }

    @Override
    public void write(long l, int bit) {
        final int bitOffset = this.bitOffset;
        final int writerIndex = byteBuf.writerIndex();
        super.write(l, bit);
        final WriteLog log = new WriteLog((bitOffset + bit + 7) >>> 3, bitOffset, bit);
        log.val1 = l & BitBuf_writer.mask(bit);
        copyWrittenBytes(log.bytes, writerIndex);
        logs.add(log);
    }

    @Override
    public void skip(int bit) {
        final int bitOffset = this.bitOffset;
        final int writerIndex = byteBuf.writerIndex();
        super.skip(bit);
        if (bit == 0) {
            return;
        }
        final int byteLen = (int) (((long) bitOffset + bit + 7) >>> 3);
        final SkipLog log = new SkipLog(byteLen, bitOffset, bit);
        copyWrittenBytes(log.bytes, writerIndex);
        logs.add(log);
    }

    private void copyWrittenBytes(byte[] bytes, int writerIndex) {
        final int written = byteBuf.writerIndex() - writerIndex;
        byteBuf.getBytes(writerIndex, bytes, 0, written);
        if (bitOffset != 0) {
            bytes[written] = b;
        }
    }

    public void finish() {
        final FinishLog log;
        if (bitOffset == 0) {
            log = null;
        } else {
            byteBuf.writeByte(b);
            log = new FinishLog(1, bitOffset, 8 - bitOffset);
            log.bytes[0] = b;
        }
        b = 0;
        bitOffset = 0;
        if (log != null) {
            logs.add(log);
        }
    }

    public Log[] takeLogs() {
        Log[] temp = logs.toArray(new Log[0]);
        logs.clear();
        return temp;
    }

    public static abstract class Log {
        public final byte[] bytes;

        public final int bitStart;
        public final int bit;

        public final int bitEnd;

        public Log(int byteLen, int bitStart, int bit) {
            this.bytes = new byte[byteLen];
            this.bitStart = bitStart;
            this.bit = bit;
            this.bitEnd = bitStart + bit - 1;
        }


        public final String getLogHex() {
            return ByteBufUtil.hexDump(bytes) + ((bitEnd & 7) == 7 ? "" : "?");
        }

        public abstract String msg();
    }

    public static class FinishLog extends Log {

        public boolean write;

        public FinishLog(int byteLen, int bitStart, int bit) {
            super(byteLen, bitStart, bit);
            this.write = true;
        }

        public FinishLog() {
            super(0, 0, 0);
            this.write = false;
        }

        public final String getLogBit() {
            final StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Strings.padStart(Integer.toBinaryString(b & 0xff), 8, '0'));
            }
            return sb.substring(bitStart, bitEnd + 1);
        }

        @Override
        public String msg() {
            if (write) {
                return StringUtil.format("finish write bit_hex[{}] bit_pos[{}-{}] bit_binary[{}]", getLogHex().toUpperCase(), bitStart, bitEnd, getLogBit());
            } else {
                return StringUtil.format("finish no write");
            }
        }
    }

    public static class SkipLog extends Log {
        public SkipLog(int byteLen, int bitStart, int bit) {
            super(byteLen, bitStart, bit);
        }

        @Override
        public String msg() {
            return StringUtil.format("skip bit_hex[{}] bit_pos[{}-{}]", getLogHex().toUpperCase(), bitStart, bitEnd);
        }
    }

    public static class WriteLog extends Log {
        public long val1;

        public WriteLog(int byteLen, int bitStart, int bit) {
            super(byteLen, bitStart, bit);
        }

        public final String getLogBit(long l) {
            return Strings.padStart(Long.toBinaryString(l), bit, '0');
        }

        @Override
        public String msg() {
            return StringUtil.format("write bit_val[{}] bit_binary[{}] bit_hex[{}] bit_pos[{}-{}]",
                    val1,
                    getLogBit(val1),
                    getLogHex().toUpperCase(),
                    bitStart, bitEnd);
        }
    }
}
