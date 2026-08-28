package cn.bcd.app.transponder.gb32960.v2016;

import cn.bcd.app.transponder.gb32960.Monitor;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PlatformLoginData;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class DataInboundHandler_v2016 extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(DataInboundHandler_v2016.class);
    private PlatformLoginData platformLoginData;
    private Monitor.ClientMetric clientMetric;

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        if (PacketUtil.getReplyFlag(msg) != 0xFE) {
            logger.debug("ignore non-command frame from [{}]", ctx.channel().remoteAddress());
            return;
        }

        PacketFlag packetFlag;
        try {
            packetFlag = PacketUtil.getPacketFlag(msg);
        } catch (RuntimeException e) {
            logger.warn("close client [{}] for unknown command", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        if (packetFlag == null) {
            logger.warn("close client [{}] for unknown command", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        if (clientMetric == null) {
            clientMetric = new Monitor.ClientMetric(new Date());
            Monitor.clientMetrics.add(clientMetric);
        }

        byte[] response = PacketUtil.build_bytes_common_response(msg, (byte) 0x01);
        switch (packetFlag) {
            case platform_login_data -> {
                Packet packet = Packet.read(msg);
                platformLoginData = (PlatformLoginData) packet.data;
                clientMetric.username = platformLoginData.username;
                logger.info("receive platform login: username[{}] sn[{}]",
                        platformLoginData.username, platformLoginData.sn);
                ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
            }
            case platform_logout_data -> {
                Packet packet = Packet.read(msg);
                logger.info("receive platform logout: dataType[{}]", packet.data.getClass().getSimpleName());
                ctx.writeAndFlush(Unpooled.wrappedBuffer(response)).addListener(ChannelFutureListener.CLOSE);
            }
            default -> {
                if (platformLoginData == null) {
                    clientMetric.unLoginCount.increment();
                } else {
                    clientMetric.loginCount.increment();
                }
                ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (clientMetric != null) {
            Monitor.clientMetrics.remove(clientMetric);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("handle client [{}] data error", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
