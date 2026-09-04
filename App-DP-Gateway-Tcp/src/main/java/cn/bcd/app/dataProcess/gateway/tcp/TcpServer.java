package cn.bcd.app.dataProcess.gateway.tcp;

import cn.bcd.app.dataProcess.gateway.tcp.v2016.DataHandler_v2016;
import cn.bcd.app.dataProcess.gateway.tcp.v2025.DataHandler_v2025;
import cn.bcd.lib.base.init.Initializable;
import cn.bcd.lib.base.util.StringUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@EnableConfigurationProperties(GatewayProp.class)
@Component
public class TcpServer implements CommandLineRunner {

    final Logger logger = LoggerFactory.getLogger(TcpServer.class);
    @Autowired
    GatewayProp gatewayProp;

    @Autowired(required = false)
    List<Initializable> initList;

    @Autowired
    List<DataHandler_v2016> handlers_v2016;
    @Autowired
    List<DataHandler_v2025> handlers_v2025;

    private void logHandlers() {
        logger.info("""
                ---------DataHandler_v2016---------
                {}
                -----------------------------------
                """, handlers_v2016.stream()
                .map(e -> StringUtil.format("order[{}] class[{}]",
                        Optional.ofNullable(e.getClass().getAnnotation(Order.class)).map(v -> v.value() + "").orElse(""),
                        e.getClass().getName()))
                .collect(Collectors.joining("\n")));

        logger.info("""
                ---------DataHandler_v2025---------
                {}
                -----------------------------------
                """, handlers_v2025.stream()
                .map(e -> StringUtil.format("order[{}] class[{}]",
                        Optional.ofNullable(e.getClass().getAnnotation(Order.class)).map(v -> v.value() + "").orElse(""),
                        e.getClass().getName()))
                .collect(Collectors.joining("\n")));
    }

    public void run(String... args) throws Exception {

        //初始化组件
        Initializable.initByOrder(initList);

        //打印handler顺序
        logHandlers();

        Thread.startVirtualThread(() -> {
            final EventLoopGroup boosGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
            final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
            try {
                final ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(boosGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(
                        new ChannelInitializer<>() {
                            @Override
                            protected void initChannel(Channel ch) {
                                ch.pipeline().addLast(new IdleStateHandler(0L, 0L, 30L, TimeUnit.SECONDS));
                                ch.pipeline().addLast(new DispatchHandler(handlers_v2016,handlers_v2025));
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
}
