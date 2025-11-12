package cn.bcd.app.transponder.gb32960.v2016;

import cn.bcd.app.transponder.gb32960.Monitor;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PlatformLoginData;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class DataInboundHandler_v2016 extends ChannelInboundHandlerAdapter {
    static Logger logger = LoggerFactory.getLogger(DataInboundHandler_v2016.class);
    PlatformLoginData platformLoginData;
    Monitor.ClientMetric clientMetric;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        //读取数据
        ByteBuf byteBuf = (ByteBuf) msg;
        PacketFlag packetFlag = PacketUtil.getPacketFlag(byteBuf);
        if (clientMetric == null) {
            Date time = PacketUtil.getTime(byteBuf);
            clientMetric = new Monitor.ClientMetric(time);
            Monitor.clientMetrics.add(clientMetric);
        }
        //构建应答报文
        byte[] response = PacketUtil.build_bytes_common_response(byteBuf, (byte) 0x00);
        switch (packetFlag) {
            case PacketFlag.platform_login_data -> {
                Packet p = Packet.read(byteBuf);
                platformLoginData = ((PlatformLoginData) p.data);
                clientMetric.username = platformLoginData.username;
                logger.info("receive platform login:\n{}", JsonUtil.toJsonPretty(platformLoginData));
                ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
            }
            case PacketFlag.platform_logout_data -> {
                Packet p = Packet.read(byteBuf);
                logger.info("receive platform logout:\n{}", JsonUtil.toJsonPretty(p.data));
                ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
                //关闭连接
                ctx.close();
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
    public void channelInactive(ChannelHandlerContext ctx) {
        if (clientMetric != null) {
            Monitor.clientMetrics.remove(clientMetric);
        }
    }
}
