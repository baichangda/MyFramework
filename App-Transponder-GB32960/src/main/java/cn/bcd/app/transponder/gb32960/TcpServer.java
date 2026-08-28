package cn.bcd.app.transponder.gb32960;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.net.InetSocketAddress;
import java.util.concurrent.Callable;

public abstract class TcpServer implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

    @CommandLine.ParentCommand
    Starter starter;

    @Override
    public Integer call() {
        EventLoopGroup bossGroup = null;
        EventLoopGroup workerGroup = null;
        try {
            ChannelHandler childHandler = createChildHandler();
            Monitor.start();
            bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
            final ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(childHandler);
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

    protected abstract ChannelHandler createChildHandler() throws Exception;
}
