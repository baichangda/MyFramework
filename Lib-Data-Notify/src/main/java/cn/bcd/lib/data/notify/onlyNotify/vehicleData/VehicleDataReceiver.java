package cn.bcd.lib.data.notify.onlyNotify.vehicleData;

import cn.bcd.lib.data.notify.NotifyConst;
import cn.bcd.lib.data.notify.NotifyProp;
import cn.bcd.lib.data.notify.onlyNotify.Receiver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(NotifyProp.class)
@ConditionalOnProperty("lib.data.notify.vehicleData.groupId")
@Component
public class VehicleDataReceiver extends Receiver<VehicleData> {
    public VehicleDataReceiver(KafkaProperties kafkaProp, NotifyProp notifyProp) {
        super("vehicleData", NotifyConst.topic_vehicleData, notifyProp.vehicleData.groupId, kafkaProp);
    }

    @Override
    public int order() {
        return 10;
    }
}
