package cn.bcd.app.transponder.gb32960.v2016;

import cn.bcd.app.transponder.gb32960.TcpServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.concurrent.TimeUnit;

@CommandLine.Command(name = "v2016", mixinStandardHelpOptions = true)
public class Starter_v2016 extends TcpServer {
    private static final Logger logger = LoggerFactory.getLogger(Starter_v2016.class);
    private static final int MAX_FRAME_LENGTH = 10_240;

    @Override
    protected ChannelHandler createChildHandler() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new IdleStateHandler(0L, 0L, 30L, TimeUnit.SECONDS));
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof IdleStateEvent event && event.state() == IdleState.ALL_IDLE) {
                            logger.info("close idle client [{}]", ctx.channel().remoteAddress());
                            ctx.close();
                            return;
                        }
                        super.userEventTriggered(ctx, evt);
                    }
                });
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 22, 2, 1, 0));
                ch.pipeline().addLast(new DataInboundHandler_v2016());
            }
        };
    }
}
