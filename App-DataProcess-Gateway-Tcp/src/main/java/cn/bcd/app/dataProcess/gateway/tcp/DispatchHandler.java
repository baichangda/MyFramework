package cn.bcd.app.dataProcess.gateway.tcp;

import cn.bcd.app.dataProcess.gateway.tcp.v2016.DataHandler_v2016;
import cn.bcd.app.dataProcess.gateway.tcp.v2016.DataInboundHandler_v2016;
import cn.bcd.app.dataProcess.gateway.tcp.v2025.DataHandler_v2025;
import cn.bcd.app.dataProcess.gateway.tcp.v2025.DataInboundHandler_v2025;
import cn.bcd.lib.base.util.StringUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DispatchHandler extends ByteToMessageDecoder {
    private static final Logger logger = LoggerFactory.getLogger(DispatchHandler.class);

    List<DataHandler_v2016> handlers_v2016;
    List<DataHandler_v2025> handlers_v2025;

    public DispatchHandler(List<DataHandler_v2016> handlers_v2016, List<DataHandler_v2025> handlers_v2025) {
        this.handlers_v2016 = handlers_v2016;
        this.handlers_v2025 = handlers_v2025;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 2) {
            return;
        }
        final int readerIndex = in.readerIndex();
        final byte b0 = in.getByte(readerIndex);
        final byte b1 = in.getByte(readerIndex + 1);
        if (b0 == 0x23 && b1 == 0x23) {
            ctx.pipeline().addLast(new LengthFieldBasedFrameDecoder(10 * 1024, 22, 2, 1, 0));
            ctx.pipeline().addLast(new DataInboundHandler_v2016(handlers_v2016));
            ctx.pipeline().remove(this);
            out.add(in.readRetainedSlice(in.readableBytes()));
        } else if (b0 == 0x24 && b1 == 0x24) {
            ctx.pipeline().addLast(new LengthFieldBasedFrameDecoder(10 * 1024, 22, 2, 1, 0));
            ctx.pipeline().addLast(new DataInboundHandler_v2025(handlers_v2025));
            ctx.pipeline().remove(this);
            out.add(in.readRetainedSlice(in.readableBytes()));
        } else {
            logger.info("receive header[{},{}]、close channel", b0, b1);
            in.skipBytes(in.readableBytes());
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("exceptionCaught", cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            logger.info("[{}] trigger", ((IdleStateEvent) evt).state());
            ctx.channel().close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
