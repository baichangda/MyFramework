package cn.bcd.server.data.process.gateway.tcp.gb32960;

import cn.bcd.server.data.process.gateway.tcp.SessionClusterManager;
import cn.bcd.server.data.process.gateway.tcp.GatewayProp;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class Handler_gb32960 extends ChannelInboundHandlerAdapter {

    static Logger logger = LoggerFactory.getLogger(Handler_gb32960.class);
    private final SessionClusterManager sessionClusterManager;
    private final KafkaTemplate<byte[], byte[]> kafkaTemplate;
    private final String parseTopic;
    Session_gb32960 session;

    public Handler_gb32960(SessionClusterManager sessionClusterManager,
                           KafkaTemplate<byte[], byte[]> kafkaTemplate,
                           GatewayProp gatewayProp) {
        this.sessionClusterManager = sessionClusterManager;
        this.kafkaTemplate = kafkaTemplate;
        this.parseTopic = gatewayProp.parseTopic;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Monitor_gb32960.receiveNum.increment();
        Monitor_gb32960.blockingNum.increment();
        //读取数据
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        try {
            //轻量解析
            String vin = new String(bytes, 4, 21);
            if (session == null) {
                //构造会话
                session = new Session_gb32960(vin, ctx.channel());
                //发送会话通知到其他集群、踢掉无用的session
                sessionClusterManager.send(session);
            }
            //发送到解析队列
            sendToKafka(vin, bytes);
        } catch (Exception e) {
            Monitor_gb32960.blockingNum.decrement();
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
        kafkaTemplate.send(parseTopic, vin.getBytes(), value).whenComplete((sendResult, throwable) -> {
            if (throwable == null) {
                Monitor_gb32960.sendKafkaNum.increment();
            } else {
                logger.error("send failed", throwable);
            }
            Monitor_gb32960.blockingNum.decrement();
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
