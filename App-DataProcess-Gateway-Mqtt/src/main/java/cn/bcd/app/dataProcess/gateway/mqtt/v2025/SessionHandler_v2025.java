package cn.bcd.app.dataProcess.gateway.mqtt.v2025;

import cn.bcd.app.dataProcess.gateway.mqtt.GatewayProp;
import cn.bcd.app.dataProcess.gateway.mqtt.VehicleConsumeExecutorGroup;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;


@Order(10)
@Component
public class SessionHandler_v2025 implements DataHandler_v2025 {

    static Logger logger = LoggerFactory.getLogger(SessionHandler_v2025.class);

    @Autowired
    GatewayProp gatewayProp;

    @Autowired
    KafkaTemplate<String, byte[]> kafkaTemplate;

    VehicleConsumeExecutorGroup vehicleConsumeExecutorGroup;

    @Override
    public void setConsumeExecutorGroup(VehicleConsumeExecutorGroup vehicleConsumeExecutorGroup) {
        this.vehicleConsumeExecutorGroup = vehicleConsumeExecutorGroup;
    }

    @Override
    public void init(String vin, Context_v2025 context) throws Exception {
        //发送会话通知到其他集群、踢掉无用的session
        String sessionTopic = gatewayProp.sessionTopic;
        String id = gatewayProp.id;
        String msg = vin + "," + id + "," + DateUtil.CacheMillisecond.current();
        kafkaTemplate.send(new ProducerRecord<>(sessionTopic, msg.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, Context_v2025 context) throws Exception {
    }

    @KafkaListener(topics = "${gateway.sessionTopic}")
    public void listen(ConsumerRecord<byte[], byte[]> consumerRecord) {
        //格式为 session类型,sessionId,网关id,连接的时间戳(毫秒)
        final String value = new String(consumerRecord.value());
        final String[] split = value.split(",");
        final String remoteGatewayId = split[1];
        //忽略本网关自己的通知
        if (!gatewayProp.id.equals(remoteGatewayId)) {
            final long remoteTs = Long.parseLong(split[2]);
            vehicleConsumeExecutorGroup.checkRemoveEntity(split[0], (entity) -> {
                if (entity.createTime < remoteTs) {
                    logger.debug("remove local entity[{},{}] with listen[{}]", entity.id, entity.createTime, value);
                    return true;
                } else {
                    return false;
                }
            });
        }
    }
}
