package cn.bcd.app.dataProcess.gateway.mqtt.v2025;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;

@Order(99)
@Component
public class SendKafkaHandler_v2025 implements DataHandler_v2025 {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(SendKafkaHandler_v2025.class);

    @Value("${spring.kafka.parse-topic}")
    private String topic;

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, Context_v2025 context) {
        Date receiverTime = context.receiveTime;
        byte[] bytes = DateUtil.prependDatesToBytes(data, receiverTime, new Date());
        kafkaTemplate.send(topic, vin, bytes);
        logger.info("send kafka message:{}", ByteBufUtil.hexDump(bytes));
    }
}
