package cn.bcd.lib.spring.data.notify.onlyNotify.platformStatus;

import cn.bcd.lib.spring.data.notify.NotifyConst;
import cn.bcd.lib.spring.data.notify.NotifyProp;
import cn.bcd.lib.spring.data.notify.onlyNotify.Sender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(NotifyProp.class)
@ConditionalOnProperty("lib.spring.data.notify.platformStatus.enableSender")
@Component
public class PlatformStatusSender extends Sender<PlatformStatusData> {
    public PlatformStatusSender(KafkaProperties kafkaProp) {
        super(NotifyConst.topic_platformStatus, kafkaProp);
    }
}
