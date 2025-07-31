package cn.bcd.app.data.process.gateway.tcp;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Date;

public class VehicleHandler extends ChannelInboundHandlerAdapter {

    static Logger logger = LoggerFactory.getLogger(VehicleHandler.class);
    private final SessionClusterManager sessionClusterManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final GatewayCommandReceiver gatewayCommandReceiver;
    private final String parseTopic;
    Session session;

    public VehicleHandler(SessionClusterManager sessionClusterManager,
                          RedisTemplate<String, String> redisTemplate,
                          KafkaTemplate<String, byte[]> kafkaTemplate,
                          GatewayCommandReceiver gatewayCommandReceiver,
                          GatewayProp gatewayProp) {
        this.sessionClusterManager = sessionClusterManager;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.gatewayCommandReceiver = gatewayCommandReceiver;
        this.parseTopic = gatewayProp.parseTopic;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Date receiveTime = new Date();
        Monitor.receiveNum.increment();
        Monitor.blockingNum.increment();
        //读取数据
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        try {
            //轻量解析
            String vin = PacketUtil.getVin(bytes);
            PacketFlag packetFlag = PacketUtil.getPacketFlag(bytes);
            if (Const.logEnable) {
                logger.info("receive packet vin[{}] flag[{}] hex:\n{}", vin, packetFlag, ByteBufUtil.hexDump(bytes));
            }
            if (session == null) {
                //构造会话
                session = new Session(vin, ctx.channel());
                //发送会话通知到其他集群、踢掉无用的session
                sessionClusterManager.send(session);
            }
            //刷新redis最后一包数据时间
            redisTemplate.opsForValue().set(Const.redis_key_prefix_vehicle_last_packet_time + vin, System.currentTimeMillis() + "");
            //响应下行指令的结果
            gatewayCommandReceiver.onResponse(vin, packetFlag.type, bytes);
            //发送到解析队列
            sendToKafka(vin, DateUtil.prependDatesToBytes(bytes, receiveTime, new Date()));
        } catch (Exception e) {
            Monitor.blockingNum.decrement();
            logger.error("error", e);
        }
        //响应数据
        ctx.writeAndFlush(Unpooled.wrappedBuffer(response_succeed(bytes)));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //关闭
        if (session != null) {
            session.close();
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("exceptionCaught", cause);
        //关闭
        ctx.close();
    }

    private void sendToKafka(String vin, byte[] value) {
        kafkaTemplate.send(parseTopic, vin, value).whenComplete((sendResult, throwable) -> {
            if (throwable == null) {
                Monitor.sendKafkaNum.increment();
            } else {
                logger.error("send failed", throwable);
            }
            Monitor.blockingNum.decrement();
        });

    }

    public static byte[] response_succeed(byte[] data) {
        byte[] response = new byte[31];
        System.arraycopy(data, 0, response, 0, 30);
        response[3] = 1;
        response[22] = 0;
        response[23] = 6;
        fixCode(response);
        return response;
    }

    /**
     * 修正异或校验位
     *
     * @param data 只包含一条数据的数据包
     */
    public static void fixCode(byte[] data) {
        byte xor = 0;
        int codeIndex = data.length - 1;
        for (int i = 0; i < codeIndex; i++) {
            xor ^= data[i];
        }
        data[codeIndex] = xor;
    }
}
