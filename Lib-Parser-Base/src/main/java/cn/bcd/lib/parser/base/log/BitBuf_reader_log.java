package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.util.BitBuf_reader;

import cn.bcd.lib.base.util.StringUtil;
import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

public final class BitBuf_reader_log extends BitBuf_reader {

    public List<Log> logs = new ArrayList<>();

    public BitBuf_reader_log(ByteBuf byteBuf) {
        super(byteBuf);
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
            final byte[] source2 = {
                    (byte) 0x81, (byte) 0x72, (byte) 0x40,
            };
            ByteBuf bb2 = Unpooled.wrappedBuffer(source2);
            BitBuf_reader_log bitBuf2 = new BitBuf_reader_log(bb2);
            bitBuf2.read(3, true);
            bitBuf2.read(3, true);
            bitBuf2.skip(3);
            bitBuf2.read(9, false);
        }
        System.out.println(System.currentTimeMillis() - t1);

    }

    @Override
    public long read(int bit, boolean unsigned) {
        final int bitOffset = this.bitOffset;
        final int readerIndex = byteBuf.readerIndex();
        final byte cached = b;
        final long value = super.read(bit, unsigned);
        final ReadLog log = new ReadLog((bitOffset + bit + 7) >>> 3, bitOffset, bit, unsigned);
        copyReadBytes(log.bytes, readerIndex, bitOffset, cached);
        log.val1 = value & BitBuf_reader.mask(bit);
        log.val2 = value;
        logs.add(log);
        return value;
    }

    @Override
    public void skip(int bit) {
        final int bitOffset = this.bitOffset;
        final int readerIndex = byteBuf.readerIndex();
        final byte cached = b;
        super.skip(bit);
        if (bit == 0) {
            return;
        }
        final int byteLen = (int) (((long) bitOffset + bit + 7) >>> 3);
        final SkipLog log = new SkipLog(byteLen, bitOffset, bit);
        copyReadBytes(log.bytes, readerIndex, bitOffset, cached);
        logs.add(log);
    }

    private void copyReadBytes(byte[] bytes, int readerIndex, int bitOffset, byte cached) {
        final int start = bitOffset == 0 ? 0 : 1;
        if (start != 0) {
            bytes[0] = cached;
        }
        byteBuf.getBytes(readerIndex, bytes, start, bytes.length - start);
    }

    public void finish() {
        final FinishLog log;
        if (bitOffset == 0) {
            log = null;
        } else {
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

        public boolean skip;

        public FinishLog(int byteLen, int bitStart, int bit) {
            super(byteLen, bitStart, bit);
            this.skip = true;
        }

        public FinishLog() {
            super(0, 0, 0);
            this.skip = false;
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
            if (skip) {
                return StringUtil.format("finish skip bit_hex[{}] bit_pos[{}-{}] bit_binary[{}]", getLogHex().toUpperCase(), bitStart, bitEnd, getLogBit());
            } else {
                return StringUtil.format("finish no skip");
            }
        }
    }

    public static class SkipLog extends Log {

        public SkipLog(int byteLen, int bitStart, int bit) {
            super(byteLen, bitStart, bit);
        }

        public final String getLogBit() {
            final StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Strings.padStart(Integer.toBinaryString(b & 0xff), 8, '0'));
            }
            return sb.substring(bitStart, bitEnd + 1);
        }

        public final String msg() {
            return StringUtil.format("skip bit_hex[{}] bit_pos[{}-{}] bit_binary[{}]", getLogHex().toUpperCase(), bitStart, bitEnd, getLogBit());
        }
    }

    public static class ReadLog extends Log {

        public final boolean unsigned;

        //原始值
        public long val1;
        //最终解析值
        public long val2;

        public ReadLog(int byteLen, int bitStart, int bit, boolean unsigned) {
            super(byteLen, bitStart, bit);
            this.unsigned = unsigned;
        }

        public final String getLogBit(long l) {
            return Strings.padStart(Long.toBinaryString(l), bit, '0');
        }

        @Override
        public String msg() {
            return StringUtil.format("read bit_hex[{}] bit_pos[{}-{}] bit_unsigned[{}] bit_binary[{}->{}] bit_val[{}->{}]",
                    getLogHex().toUpperCase(),
                    bitStart, bitEnd,
                    unsigned ? "yes" : "no",
                    getLogBit(val1), getLogBit(val2),
                    val1, val2);
        }
    }
}
