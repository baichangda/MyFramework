package cn.bcd.app.dataProcess.gateway.mqtt.v2025;

import cn.bcd.app.dataProcess.gateway.mqtt.MqttProp;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class ResponseHandler_v2025 implements DataHandler_v2025 {

    static Logger logger = LoggerFactory.getLogger(ResponseHandler_v2025.class);


    @Autowired
    MqttProp mqttProp;

    @Autowired
    public Mqtt5AsyncClient client;

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, Context_v2025 context) {
        if ((data[3] & 0xFF) == 0xFE) {
            byte[] responseByte = PacketUtil.build_bytes_common_response(data, (byte) 1);
            logger.info("response msg vin[{}] type[{}]:\n{}", vin, flag, ByteBufUtil.hexDump(responseByte));
            client.publish(Mqtt5Publish.builder().topic(mqttProp.getResponseTopicPrefix() + vin).payload(responseByte).build());
        }
    }
}
