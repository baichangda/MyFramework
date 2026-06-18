package cn.bcd.app.transponder.gb32960.v2025;

import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataInboundHandler_v2025 extends SimpleChannelInboundHandler<ByteBuf> {
    static Logger logger = LoggerFactory.getLogger(DataInboundHandler_v2025.class);
    @Override
    public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        //应答成功
        byte[] response = PacketUtil.build_bytes_common_response(msg, (byte) 0x00);
        ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
    }
}
