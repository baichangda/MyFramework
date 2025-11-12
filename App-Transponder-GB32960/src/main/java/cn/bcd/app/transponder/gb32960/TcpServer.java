package cn.bcd.app.transponder.gb32960;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public abstract class TcpServer implements Runnable {
    static Logger logger = LoggerFactory.getLogger(TcpServer.class);
    @CommandLine.ParentCommand
    Starter starter;

    @Override
    public void run() {
        //启动监控
        Monitor.start();

        final EventLoopGroup boosGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            final ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(boosGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(
                    new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new IdleStateHandler(0L, 0L, 30L, TimeUnit.SECONDS));
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(10 * 1024, 22, 2, 1, 0));
                            init(ch);
                        }
                    }
            );
            final ChannelFuture channelFuture = serverBootstrap.bind(new InetSocketAddress(starter.port)).sync();
            logger.info("server listen tcp port[{}]", starter.port);
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            logger.error("run error", e);
        } finally {
            boosGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    protected abstract void init(Channel ch);
}
