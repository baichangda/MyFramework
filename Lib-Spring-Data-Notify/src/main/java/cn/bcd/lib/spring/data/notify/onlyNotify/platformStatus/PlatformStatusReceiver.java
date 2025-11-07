package cn.bcd.lib.spring.data.notify.onlyNotify.platformStatus;

import cn.bcd.lib.base.common.Initializable;
import cn.bcd.lib.spring.data.notify.NotifyConst;
import cn.bcd.lib.spring.data.notify.NotifyProp;
import cn.bcd.lib.spring.data.notify.onlyNotify.Receiver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(NotifyProp.class)
@ConditionalOnProperty("lib.data.notify.platformStatus.groupId")
@Component
public class PlatformStatusReceiver extends Receiver<PlatformStatusData> implements Initializable {
    public PlatformStatusReceiver(KafkaProperties kafkaProp, NotifyProp notifyProp) {
        super(PlatformStatusReceiver.class.getSimpleName(), NotifyConst.topic_platformStatus, notifyProp.platformStatus.groupId, kafkaProp);
    }
}