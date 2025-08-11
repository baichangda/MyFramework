package cn.bcd.app.dataProcess.transfer.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpClientHandler extends ChannelInboundHandlerAdapter {

    static Logger logger= LoggerFactory.getLogger(TcpClientHandler.class);

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Client.onDisconnect();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Client.onMessage((ByteBuf) msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("error",cause);
    }
}
