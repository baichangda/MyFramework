package cn.bcd.app.dataProcess.gateway.tcp.v2025;

import cn.bcd.app.dataProcess.gateway.tcp.GatewayProp;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Order(999)
@Component
public class SendKafkaHandler_v2025 implements DataHandler_v2025 {
    @Autowired
    GatewayProp gatewayProp;

    @Autowired
    KafkaTemplate<String,byte[]> kafkaTemplate;

    static Logger logger= LoggerFactory.getLogger(SendKafkaHandler_v2025.class);

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, Context_v2025 context) throws Exception {
        kafkaTemplate.send(gatewayProp.parseTopic, vin, data);
    }
}
