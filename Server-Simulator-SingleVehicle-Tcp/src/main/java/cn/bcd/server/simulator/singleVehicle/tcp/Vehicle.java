package cn.bcd.server.simulator.singleVehicle.tcp;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.executor.SingleThreadExecutor;
import cn.bcd.lib.base.executor.TaskResult;
import cn.bcd.lib.parser.base.anno.data.NumVal_byte;
import cn.bcd.lib.parser.protocol.gb32960.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.data.PacketFlag;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


public class Vehicle {
    static NioEventLoopGroup tcp_workerGroup = new NioEventLoopGroup();
    public final String vin;
    public VehicleData vehicleData;
    public final SingleThreadExecutor executor;

    Channel channel;
    Runnable onConnected;
    Runnable onDisconnected;
    Consumer<byte[]> onSend;
    Consumer<byte[]> onReceive;
    Consumer<VehicleData> onDataUpdate;
    ScheduledFuture<?> scheduledFuture;

    public Vehicle(String vin, SingleThreadExecutor executor) {
        this.vin = vin;
        this.executor = executor;
    }

    public CompletableFuture<TaskResult<Void>> init() {
        return executor.submit(() -> {
            this.vehicleData = new VehicleData();
            vehicleData.init();
        });
    }

    public CompletableFuture<TaskResult<Void>> destroy() {
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

    public CompletableFuture<TaskResult<Void>> connect(String host, int port,
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

    public CompletableFuture<TaskResult<Void>> disconnect() {
        return executor.submit(() -> {
            if (channel != null) {
                channel.close();
            }
        });
    }

    public CompletableFuture<TaskResult<Void>> startSendRunData() {
        return executor.submit(() -> {
            if (scheduledFuture == null) {
                scheduledFuture = executor.scheduleAtFixedRate(this::send_vehicleRunData, 1, 10, TimeUnit.SECONDS);
            }
        });
    }

    public CompletableFuture<TaskResult<Void>> stopSendRunData() {
        return executor.submit(() -> {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
            }
        });
    }

    public CompletableFuture<TaskResult<Void>> send(byte[] data) {
        return executor.submit(() -> {
            if (channel != null) {
                channel.writeAndFlush(Unpooled.wrappedBuffer(data));
            }
        });
    }

    public void onConnected() {
        executor.execute(() -> {
            startSendRunData();
            if (onConnected != null) {
                onConnected.run();
            }
        });
    }

    public void onMessage(byte[] data) {
        executor.execute(() -> {
            if (onConnected != null) {
                onReceive.accept(data);
            }
        });
    }

    public void onDisconnected() {
        executor.execute(() -> {
            stopSendRunData();
            if (onDisconnected != null) {
                onDisconnected.run();
            }
        });
    }

    private void send_vehicleRunData() {
        Packet packet = new Packet();
        packet.header = new byte[]{0x23, 0x23};
        packet.flag = PacketFlag.vehicle_run_data;
        packet.replyFlag = 0xfe;
        packet.vin = vin;
        packet.encodeWay = new NumVal_byte(0, (byte) 1);
        packet.code = 0;
        vehicleData.vehicleRunData.collectTime = new Date();
        packet.data = vehicleData.vehicleRunData;
        ByteBuf buffer = packet.toByteBuf_fixAll();
        byte[] arr = new byte[buffer.readableBytes()];
        buffer.readBytes(arr);
        send(arr);
        onSend.accept(arr);
    }
}
