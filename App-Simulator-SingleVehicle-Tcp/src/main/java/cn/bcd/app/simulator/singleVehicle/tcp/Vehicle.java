package cn.bcd.app.simulator.singleVehicle.tcp;

import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.EventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Vehicle {

    private static final Logger logger = LoggerFactory.getLogger(Vehicle.class);
    private static final MultiThreadIoEventLoopGroup TCP_WORKER_GROUP =
            new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private static final int MAX_FRAME_LENGTH = 10 * 1024;
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    public interface Listener {
        void onTcpConnected();

        void onTcpConnectFailed(Throwable cause);

        void onTcpDisconnected();

        void onTcpSend(byte[] data);

        void onTcpReceive(byte[] data);
    }

    public final String vin;
    private final int sendPeriod;
    private final Function<String, VehicleData> vehicleDataFactory;
    private final EventExecutor scheduler;
    private final Listener listener;

    private VehicleData vehicleData;
    private volatile Channel channel;
    private ScheduledFuture<?> sendTask;

    public Vehicle(String vin,
                   int sendPeriod,
                   Function<String, VehicleData> vehicleDataFactory,
                   EventExecutor scheduler,
                   Listener listener) {
        this.vin = vin;
        this.sendPeriod = sendPeriod;
        this.vehicleDataFactory = vehicleDataFactory;
        this.scheduler = scheduler;
        this.listener = listener;
    }

    public VehicleData init() {
        vehicleData = vehicleDataFactory.apply(vin);
        vehicleData.init();
        return vehicleData;
    }

    public void destroy() {
        stopSending();
        Channel current = channel;
        channel = null;
        if (current != null) {
            current.close();
        }
    }

    public void connect(String host, int port) {
        Channel current = channel;
        if (current != null && current.isOpen()) {
            throw new IllegalStateException("tcp connection is already active");
        }

        Bootstrap bootstrap = new Bootstrap()
                .group(TCP_WORKER_GROUP)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 22, 2, 1, 0));
                        ch.pipeline().addLast(new TcpClientHandler(Vehicle.this));
                    }
                });

        ChannelFuture connectFuture = bootstrap.connect(host, port);
        channel = connectFuture.channel();
        connectFuture.addListener(future -> {
            if (!future.isSuccess()) {
                clearChannel(connectFuture.channel());
                listener.onTcpConnectFailed(future.cause());
            }
        });
    }

    public void updateVehicleData(VehicleData newVehicleData) {
        vehicleData = newVehicleData;
    }

    public VehicleData vehicleData() {
        return vehicleData;
    }

    void onConnected(Channel connectedChannel) {
        if (connectedChannel == channel) {
            listener.onTcpConnected();
        }
    }

    void onDisconnected(Channel disconnectedChannel) {
        if (clearChannel(disconnectedChannel)) {
            listener.onTcpDisconnected();
        }
    }

    void onMessage(Channel sourceChannel, byte[] data) {
        if (sourceChannel == channel) {
            listener.onTcpReceive(data);
        }
    }

    public void startSending() {
        if (sendTask == null) {
            sendTask = scheduler.scheduleAtFixedRate(this::sendVehicleRunData,
                    1, sendPeriod, TimeUnit.SECONDS);
        }
    }

    public void stopSending() {
        if (sendTask != null) {
            sendTask.cancel(false);
            sendTask = null;
        }
    }

    private boolean clearChannel(Channel expectedChannel) {
        if (channel == expectedChannel) {
            channel = null;
            return true;
        }
        return false;
    }

    private void sendVehicleRunData() {
        Channel current = channel;
        if (current == null || !current.isActive()) {
            return;
        }

        try {
            byte[] data = vehicleData.onSend_vehicleRunDataToBytes();
            logger.info("send message vin[{}] type[{}]:\n{}",
                    PacketUtil.getVin(data), PacketUtil.getPacketFlag(data), ByteBufUtil.hexDump(data));
            current.writeAndFlush(Unpooled.wrappedBuffer(data)).addListener(future -> {
                if (future.isSuccess()) {
                    listener.onTcpSend(data);
                } else {
                    logger.error("send message vin[{}] failed", vin, future.cause());
                    current.close();
                }
            });
        } catch (Exception ex) {
            logger.error("build vehicle run data vin[{}] failed", vin, ex);
        }
    }
}
