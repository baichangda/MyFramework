package cn.bcd.simulator.singleVehicle.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class TcpClientHandler extends ChannelInboundHandlerAdapter {
    public WsSession<?> session;

    public TcpClientHandler(WsSession<?> session) {
        this.session = session;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        session.tcp_onDisConnect();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        session.tcp_onMsg((ByteBuf) msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }


}
