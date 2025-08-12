package cn.bcd.app.dataProcess.gateway.tcp;

import cn.bcd.app.dataProcess.gateway.tcp.v2016.DataHandler_v2016;
import cn.bcd.app.dataProcess.gateway.tcp.v2016.DataInboundHandler_v2016;
import cn.bcd.app.dataProcess.gateway.tcp.v2025.DataHandler_v2025;
import cn.bcd.app.dataProcess.gateway.tcp.v2025.DataInboundHandler_v2025;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@ChannelHandler.Sharable
public class DispatchHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DispatchHandler.class);

    List<DataHandler_v2016> handlers_v2016;
    List<DataHandler_v2025> handlers_v2025;

    public DispatchHandler(List<DataHandler_v2016> handlers_v2016, List<DataHandler_v2025> handlers_v2025) {
        this.handlers_v2016=handlers_v2016;
        this.handlers_v2025=handlers_v2025;
        logger.info("""
                ---------DataHandler_v2016---------
                {}
                -----------------------------------
                """, handlers_v2016.stream().map(e -> e.getClass().getName()).collect(Collectors.joining("\n")));

        logger.info("""
                ---------DataHandler_v2025---------
                {}
                -----------------------------------
                """, handlers_v2025.stream().map(e -> e.getClass().getName()).collect(Collectors.joining("\n")));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = (ByteBuf) msg;
        //至少需要两个字节来判断
        if (in.readableBytes() >= 4) {
            final byte b0 = in.getByte(0);
            final byte b1 = in.getByte(1);
            if (b0 == 0x23 && b1 == 0x23) {
                ctx.pipeline().addLast(new LengthFieldBasedFrameDecoder(10 * 1024, 22, 2, 1, 0));
                ctx.pipeline().addLast(new DataInboundHandler_v2016(handlers_v2016));
                ctx.pipeline().remove(this);
            } else if (b0 == 0x24 && b1 == 0x24) {
                ctx.pipeline().addLast(new LengthFieldBasedFrameDecoder(10 * 1024, 22, 2, 1, 0));
                ctx.pipeline().addLast(new DataInboundHandler_v2025(handlers_v2025));
                ctx.pipeline().remove(this);
            } else {
                logger.info("receive header[{},{}]、close channel", b0, b1);
                // 主动断开
                ctx.channel().close();
            }
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("exceptionCaught", cause);
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
