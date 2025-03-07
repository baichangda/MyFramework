package cn.bcd.server.data.process.gateway.tcp;

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
        buf = buf.order(order);
        long frameLength;
        switch (length) {
            case 1:
                frameLength = buf.getUnsignedByte(offset);
                break;
            case 2:
                frameLength = buf.getUnsignedShortLE(offset);
                break;
            case 3:
                frameLength = buf.getUnsignedMediumLE(offset);
                break;
            case 4:
                frameLength = buf.getUnsignedIntLE(offset);
                break;
            case 8:
                frameLength = buf.getLongLE(offset);
                break;
            default:
                throw BaseException.get("not support");
        }
        return frameLength;
    }
}
