package cn.bcd.app.simulator.singleVehicle.tcp;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.executor.SingleThreadExecutor;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;


public class Vehicle {

    static Logger logger = LoggerFactory.getLogger(Vehicle.class);

    static MultiThreadIoEventLoopGroup tcp_workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    public final String vin;
    public VehicleData vehicleData;
    public final SingleThreadExecutor executor;
    public final Function<String, VehicleData> vehicleDataFunction;
    public final int sendPeriod;

    Channel channel;
    Runnable onConnected;
    Runnable onDisconnected;
    Consumer<byte[]> onSend;
    Consumer<byte[]> onReceive;
    Consumer<VehicleData> onDataUpdate;
    ScheduledFuture<?> scheduledFuture;

    public Vehicle(String vin, int sendPeriod, Function<String, VehicleData> vehicleDataFunction, SingleThreadExecutor executor) {
        this.vin = vin;
        this.sendPeriod = sendPeriod;
        this.vehicleDataFunction = vehicleDataFunction;
        this.executor = executor;
    }

    public Future<?> init() {
        return executor.submit(() -> {
            this.vehicleData = vehicleDataFunction.apply(vin);
            vehicleData.init();
        });
    }

    public Future<?> destroy() {
        return executor.submit(() -> {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
            }
            if (channel != null) {
                channel.close();
                channel = null;
            }
        });
    }

    public Future<?> connect(String host, int port,
                                           Runnable onConnected,
                                           Runnable onDisconnected,
                                           Consumer<byte[]> onSend,
                                           Consumer<byte[]> onReceive,
                                           Consumer<VehicleData> onDataUpdate
    ) {
        return executor.submit(() -> {
            this.onConnected = onConnected;
            this.onDisconnected = onDisconnected;
            this.onSend = onSend;
            this.onReceive = onReceive;
            this.onDataUpdate = onDataUpdate;
            Vehicle vehicle = this;
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(tcp_workerGroup);
            bootstrap.channel(NioSocketChannel.class).option(ChannelOption.TCP_NODELAY, true);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(10 * 1024, 22, 2, 1, 0));
                    ch.pipeline().addLast(new TcpClientHandler(vehicle));
                }
            });
            try {
                channel = bootstrap.connect(host, port).sync().channel();
            } catch (InterruptedException e) {
                throw BaseException.get(e);
            }
        });
    }

    public Future<?> disconnect() {
        return executor.submit(() -> {
            if (channel != null) {
                channel.close();
            }
        });
    }

    public void updateVehicleData(VehicleData vehicleData) {
        executor.execute(() -> {
            this.vehicleData = vehicleData;
        });
    }

    public Future<?> startSendRunData() {
        return executor.submit(() -> {
            if (scheduledFuture == null) {
                scheduledFuture = executor.scheduleAtFixedRate(this::send_vehicleRunData, 1, sendPeriod, TimeUnit.SECONDS);
            }
        });
    }

    public Future<?> stopSendRunData() {
        return executor.submit(() -> {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
            }
        });
    }

    public Future<?> send(byte[] data) {
        return executor.submit(() -> {
            if (channel != null) {
                logger.info("send message vin[{}] type[{}]:\n{}", PacketUtil.getVin(data), PacketUtil.getPacketFlag(data), ByteBufUtil.hexDump(data));
                channel.writeAndFlush(Unpooled.wrappedBuffer(data));
            }
        });
    }

    public void onConnected() {
        executor.execute(() -> {
            try {
                startSendRunData();
                if (onConnected != null) {
                    onConnected.run();
                }
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        });
    }

    public void onDisconnected() {
        executor.execute(() -> {
            try {
                stopSendRunData();
                if (onDisconnected != null) {
                    onDisconnected.run();
                }
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        });
    }

    public void onMessage(byte[] data) {
        executor.execute(() -> {
            try {
                if (onConnected != null) {
                    onReceive.accept(data);
                }
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        });
    }

    private void send_vehicleRunData() {
        byte[] arr = vehicleData.onSend_vehicleRunDataToBytes();
        send(arr);
        onSend.accept(arr);
    }
}
