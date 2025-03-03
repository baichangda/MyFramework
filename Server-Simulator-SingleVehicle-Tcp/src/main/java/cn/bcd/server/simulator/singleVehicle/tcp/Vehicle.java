package cn.bcd.server.simulator.singleVehicle.tcp;

import cn.bcd.lib.base.exception.BaseException;
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
import java.util.function.BiConsumer;


public class Vehicle {

    static NioEventLoopGroup tcp_workerGroup = new NioEventLoopGroup();

    public final String vin;
    public VehicleData vehicleData;

    public Channel channel;
    Runnable onConnected;
    Runnable onDisconnected;

    /**
     * 1、发送的上行数据
     * 2、接收到的下行数据
     */
    BiConsumer<Integer, byte[]> onMessage;

    public Vehicle(String vin) {
        this.vin = vin;

    }

    public void connect(String host, int port,
                        Runnable onConnected,
                        Runnable onDisconnected,
                        BiConsumer<Integer, byte[]> onMessage) {
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
        this.onMessage = onMessage;
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
        if (channel != null) {
            channel.close();
            channel = null;
        }
        try {
            channel = bootstrap.connect(host, port).sync().channel();
        } catch (InterruptedException e) {
            throw BaseException.get(e);
        }
    }

    public void init() {
        this.vehicleData = new VehicleData();
        vehicleData.init();
    }

    public byte[] send_vehicleRunData() {
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
        return arr;
    }

    public void send(byte[] data) {
        if (channel != null) {
            channel.writeAndFlush(Unpooled.wrappedBuffer(data));
        }
    }

    public void disconnect() {
        if (channel != null) {
            channel.close();
        }
    }

    public void onConnected() {
        if (onConnected != null) {
            onConnected.run();
        }
    }

    public void onMessage(byte[] data) {
        if (onMessage != null) {
            onMessage.accept(2, data);
        }
    }

    public void onDisconnected() {
        if (onDisconnected != null) {
            onDisconnected.run();
        }
    }
}
