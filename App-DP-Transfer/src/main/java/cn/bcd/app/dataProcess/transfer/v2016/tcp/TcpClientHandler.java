package cn.bcd.app.dataProcess.transfer.v2016.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    static Logger logger = LoggerFactory.getLogger(TcpClientHandler.class);

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        TcpClient.onDisconnect();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        byte[] bytes = ByteBufUtil.getBytes(msg);
        TcpClient.onMessage(bytes);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("error", cause);
    }
}
