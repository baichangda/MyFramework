package cn.bcd.lib.data.notify.onlyNofity.vehicleData;

import cn.bcd.lib.data.notify.Const;
import cn.bcd.lib.data.notify.NotifyProp;
import cn.bcd.lib.data.notify.onlyNofity.Receiver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(NotifyProp.class)
@ConditionalOnProperty("lib.data.notify.vehicleData.groupId")
@Component
public class VehicleDataReceiver extends Receiver<VehicleData> {
    public VehicleDataReceiver(KafkaProperties kafkaProp, NotifyProp notifyProp) {
        super("vehicleData", Const.topic_vehicleData, notifyProp.vehicleData.groupId, kafkaProp);
    }
}
