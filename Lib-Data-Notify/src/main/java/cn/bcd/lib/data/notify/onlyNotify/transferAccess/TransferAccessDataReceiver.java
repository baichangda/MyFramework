package cn.bcd.lib.data.notify.onlyNotify.transferAccess;

import cn.bcd.lib.data.notify.NotifyConst;
import cn.bcd.lib.data.notify.NotifyProp;
import cn.bcd.lib.data.notify.onlyNotify.Receiver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(NotifyProp.class)
@ConditionalOnProperty("lib.data.notify.transferAccess.groupId")
@Component
public class TransferAccessDataReceiver extends Receiver<TransferAccessData> {
    public TransferAccessDataReceiver(KafkaProperties kafkaProp, NotifyProp notifyProp) {
        super("transferAccess", NotifyConst.topic_transferAccess, notifyProp.transferAccess.groupId, kafkaProp);
    }
}
