package cn.bcd.app.transponder.gb32960.v2025;

import cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataInboundHandler_v2025 extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(DataInboundHandler_v2025.class);

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        if (PacketUtil.getReplyFlag(msg) != 0xFE) {
            logger.debug("ignore non-command frame from [{}]", ctx.channel().remoteAddress());
            return;
        }
        byte[] response = PacketUtil.build_bytes_common_response(msg, (byte) 0x01);
        ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("handle client [{}] data error", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
