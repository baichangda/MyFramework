package cn.bcd.server.dataProcess.gateway.tcp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@EnableConfigurationProperties(GatewayProp.class)
@Component
public class TcpServer implements CommandLineRunner {

    final Logger logger = LoggerFactory.getLogger(TcpServer.class);
    @Autowired
    GatewayProp gatewayProp;
    @Autowired
    Handler_dispatch handler_dispatch;

    @Autowired
    List<Monitor> monitorList;

    public void run(String... args) throws Exception {
        Thread.startVirtualThread(() -> {
            startMonitor();
            final EventLoopGroup boosGroup = new NioEventLoopGroup();
            final EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                final ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(boosGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(
                        new ChannelInitializer<>() {
                            @Override
                            protected void initChannel(Channel ch) {
                                ch.pipeline().addLast(new IdleStateHandler(0L, 0L, 30L, TimeUnit.SECONDS));
                                ch.pipeline().addLast(handler_dispatch);
                            }
                        }
                );
                final ChannelFuture channelFuture = serverBootstrap.bind(new InetSocketAddress(gatewayProp.tcpPort)).sync();
                logger.info("server listen tcp port[{}]", gatewayProp.tcpPort);
                channelFuture.channel().closeFuture().sync();
            } catch (Exception e) {
                logger.error("run error", e);
            } finally {
                boosGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        });
    }

    public void startMonitor() {
        Duration period = Duration.ofSeconds(3);
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            StringBuilder sb = new StringBuilder();
            for (Monitor monitor : monitorList) {
                sb.append("\n");
                sb.append(monitor.log(period));
            }
            logger.info("{}", sb);
        }, period.toSeconds(), period.toSeconds(), TimeUnit.SECONDS);
    }

}
