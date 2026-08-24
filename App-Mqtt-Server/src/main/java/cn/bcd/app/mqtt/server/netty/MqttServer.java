package cn.bcd.app.mqtt.server.netty;

import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Component
public class MqttServer {

    private final MqttServerProperties properties;
    private final MqttChannelInitializer channelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public MqttServer(MqttServerProperties properties, MqttChannelInitializer channelInitializer) {
        this.properties = properties;
        this.channelInitializer = channelInitializer;
    }

    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        validateProperties();
        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            serverChannel = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(channelInitializer)
                    .bind(new InetSocketAddress(properties.getBindAddress(), properties.getPort()))
                    .syncUninterruptibly()
                    .channel();
        } catch (RuntimeException | Error e) {
            shutdownEventLoops();
            throw e;
        }
    }

    public synchronized void stop() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        shutdownEventLoops();
    }

    public synchronized boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    public synchronized int getBoundPort() {
        if (serverChannel == null) {
            return -1;
        }
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    private void validateProperties() {
        if (properties.getBindAddress() == null || properties.getBindAddress().isBlank()) {
            throw new IllegalArgumentException("mqtt.server.bind-address must not be blank");
        }
        if (properties.getPort() < 0 || properties.getPort() > 65535) {
            throw new IllegalArgumentException("mqtt.server.port must be between 0 and 65535");
        }
        if (properties.getMaxPacketSize() <= 0) {
            throw new IllegalArgumentException("mqtt.server.max-packet-size must be greater than 0");
        }
        if (properties.getMaxClientIdLength() <= 0) {
            throw new IllegalArgumentException("mqtt.server.max-client-id-length must be greater than 0");
        }
    }

    private void shutdownEventLoops() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
            bossGroup = null;
        }
    }
}
