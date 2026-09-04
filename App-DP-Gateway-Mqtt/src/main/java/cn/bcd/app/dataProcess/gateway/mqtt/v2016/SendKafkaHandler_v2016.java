package cn.bcd.app.dataProcess.gateway.mqtt.v2016;

import cn.bcd.app.dataProcess.gateway.mqtt.GatewayProp;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;

@Order(99)
@Component
public class SendKafkaHandler_v2016 implements DataHandler_v2016 {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(SendKafkaHandler_v2016.class);

    @Autowired
    private GatewayProp gatewayProp;

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, Context_v2016 context) {
        Date receiverTime = context.receiveTime;
        byte[] bytes = DateUtil.prependDatesToBytes(data, receiverTime, new Date());
        kafkaTemplate.send(gatewayProp.parseTopic, vin, bytes);
        logger.info("send kafka message:{}", ByteBufUtil.hexDump(bytes));
    }
}
