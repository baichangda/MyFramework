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

/** 管理 MQTT TCP 监听端口及 Netty 事件循环的启动和释放。 */
@Component
public class MqttServer {

    private final MqttServerProperties properties;
    private final MqttChannelInitializer channelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /**
     * 创建使用指定监听配置和通道初始化器的服务。
     *
     * @param properties 服务配置
     * @param channelInitializer 通道初始化器
     */
    public MqttServer(MqttServerProperties properties, MqttChannelInitializer channelInitializer) {
        this.properties = properties;
        this.channelInitializer = channelInitializer;
    }

    /** 幂等启动 Netty 事件循环并绑定监听端口。 */
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        validateProperties();
        // boss 只负责接受连接，实际网络读写交给独立 worker 事件循环组。
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
            // 绑定失败时释放已经创建的线程，避免 Spring 启动失败后残留非守护线程。
            shutdownEventLoops();
            throw e;
        }
    }

    /** 幂等关闭监听通道及全部事件循环。 */
    public synchronized void stop() {
        // 先关闭监听通道停止接收新连接，再释放工作线程和接收线程。
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

    /** 在创建线程前校验监听与解码限制配置。 */
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

    /** 按工作组、接收组的顺序优雅关闭事件循环。 */
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
