package cn.bcd.app.dataProcess.gateway.tcp.v2016;

import cn.bcd.app.dataProcess.gateway.tcp.GatewayProp;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Order(999)
@Component
public class SendKafkaHandler implements DataHandler_v2016{

    @Autowired
    GatewayProp gatewayProp;

    @Autowired
    KafkaTemplate<String,byte[]> kafkaTemplate;

    static Logger logger= LoggerFactory.getLogger(SendKafkaHandler.class);

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, VehicleCacheData_v2016 vehicleCacheData) throws Exception {
        kafkaTemplate.send(gatewayProp.parseTopic, vin, data);
    }
}
