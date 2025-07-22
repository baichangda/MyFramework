package cn.bcd.app.data.process.gateway.tcp;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SessionClusterManager {
    static ExecutorService pool_sendKafka_sessionNotify = Executors.newFixedThreadPool(1);
    static Logger logger = LoggerFactory.getLogger(SessionClusterManager.class);

    @Autowired
    GatewayProp gatewayProp;

    @Autowired
    KafkaTemplate<byte[], byte[]> kafkaTemplate;

    @KafkaListener(topics = "${gateway.sessionTopic}")
    public void listen(ConsumerRecord<byte[], byte[]> consumerRecord) {
        //格式为 session类型,sessionId,网关id,连接的时间戳(毫秒)
        final String value = new String(consumerRecord.value());
        final String[] split = value.split(",");
        final String remoteGatewayId = split[1];
        //忽略本网关自己的通知
        if (!remoteGatewayId.equals(gatewayProp.id)) {
            final Session local = Session.getSession(split[0]);
            if (local != null) {
                final long remoteTs = Long.parseLong(split[2]);
                if (local.createTs < remoteTs) {
                    local.close();
                    logger.debug("close local session[{},{}] with listen[{}]", local.id, local.createTs, value);
                }
            }
        }

    }

    public void send(Session session) {
        String sessionTopic = gatewayProp.sessionTopic;
        String id = gatewayProp.id;
        pool_sendKafka_sessionNotify.execute(() -> {
            String msg = session.id + "," + id + "," + session.createTs;
            kafkaTemplate.send(new ProducerRecord<>(sessionTopic, msg.getBytes(StandardCharsets.UTF_8)));
        });
    }
}
