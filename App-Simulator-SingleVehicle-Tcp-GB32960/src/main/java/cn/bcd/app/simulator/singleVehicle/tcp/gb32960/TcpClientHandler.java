package cn.bcd.app.simulator.singleVehicle.tcp.gb32960;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class TcpClientHandler extends ChannelInboundHandlerAdapter {
    public Vehicle vehicle;

    public TcpClientHandler(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        vehicle.onConnected();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        vehicle.onDisconnected();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        byte[] bytes=new byte[((ByteBuf) msg).readableBytes()];
        ((ByteBuf) msg).readBytes(bytes);
        vehicle.onMessage(bytes);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }


}
