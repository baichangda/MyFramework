package cn.bcd.app.simulator.singleVehicle.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;

public class TcpClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
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
    public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        byte[] bytes = new byte[msg.readableBytes()];
        msg.readBytes(bytes);
        vehicle.onMessage(bytes);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }


}
