package cn.bcd.app.dataProcess.gateway.tcp;

import cn.bcd.lib.base.exception.BaseException;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.ByteOrder;

public class LengthFieldBasedFrameDecoder_smallEndian extends LengthFieldBasedFrameDecoder {
    public LengthFieldBasedFrameDecoder_smallEndian(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected long getUnadjustedFrameLength(ByteBuf buf, int offset, int length, ByteOrder order) {
        return switch (length) {
            case 1 -> buf.getUnsignedByte(offset);
            case 2 -> buf.getUnsignedShortLE(offset);
            case 3 -> buf.getUnsignedMediumLE(offset);
            case 4 -> buf.getUnsignedIntLE(offset);
            case 8 -> buf.getLongLE(offset);
            default -> throw BaseException.get("not support");
        };
    }
}
