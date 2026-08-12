package cn.bcd.app.simulator.singleVehicle.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger logger = LoggerFactory.getLogger(TcpClientHandler.class);
    private final Vehicle vehicle;
    private boolean connected;

    public TcpClientHandler(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        connected = true;
        vehicle.onConnected(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (connected) {
            vehicle.onDisconnected(ctx.channel());
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        byte[] bytes = new byte[msg.readableBytes()];
        msg.readBytes(bytes);
        vehicle.onMessage(ctx.channel(), bytes);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("tcp connection error vin[{}] remoteAddress[{}]",
                vehicle.vin, ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
