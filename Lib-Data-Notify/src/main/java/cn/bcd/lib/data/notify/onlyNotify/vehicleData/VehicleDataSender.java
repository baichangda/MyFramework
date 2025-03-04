package cn.bcd.lib.data.notify.onlyNotify.vehicleData;

import cn.bcd.lib.data.notify.Const;
import cn.bcd.lib.data.notify.NotifyProp;
import cn.bcd.lib.data.notify.onlyNotify.Sender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(NotifyProp.class)
@ConditionalOnProperty("lib.data.notify.vehicleData.enableSender")
@Component
public class VehicleDataSender extends Sender<VehicleData> {
    public VehicleDataSender(KafkaProperties kafkaProp) {
        super(Const.topic_vehicleData, kafkaProp);
    }
}
