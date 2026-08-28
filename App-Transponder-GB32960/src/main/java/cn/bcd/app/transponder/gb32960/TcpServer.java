package cn.bcd.app.transponder.gb32960;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public abstract class TcpServer implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);
    private static final int MAX_FRAME_LENGTH = 1024;

    @CommandLine.ParentCommand
    Starter starter;

    @Override
    public Integer call() {
        EventLoopGroup bossGroup = null;
        EventLoopGroup workerGroup = null;
        try {
            beforeStart();
            Monitor.start();
            bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
            final ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(
                    new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            // TLS 等传输层处理必须位于明文协议解码器之前。
                            initTransport(ch);
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
                            init(ch);
                        }
                    }
            );
            final ChannelFuture channelFuture = serverBootstrap.bind(new InetSocketAddress(starter.port)).sync();
            logger.info("server listen tcp port[{}]", starter.port);
            channelFuture.channel().closeFuture().sync();
            return CommandLine.ExitCode.OK;
        } catch (Exception e) {
            logger.error("run error", e);
            return CommandLine.ExitCode.SOFTWARE;
        } finally {
            Monitor.stop();
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().syncUninterruptibly();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().syncUninterruptibly();
            }
        }
    }

    protected void beforeStart() throws Exception {
    }

    protected void initTransport(Channel ch) {
    }

    protected abstract void init(Channel ch);
}
