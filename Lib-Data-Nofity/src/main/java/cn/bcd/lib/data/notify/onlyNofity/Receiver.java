package cn.bcd.lib.data.notify.onlyNofity;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;

import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.function.Consumer;

public class Receiver<T> extends ThreadDrivenKafkaConsumer {

    private final Logger logger = org.slf4j.LoggerFactory.getLogger(Receiver.class);

    @Autowired(required = false)
    public Consumer<T> consumer;

    public final Class<T> clazz;

    public Receiver(String name, String topic, String groupId, KafkaProperties kafkaProp) {
        super(name,
                false,
                1,
                1000,
                1000,
                true,
                0,
                0,
                topic, null);
        this.clazz = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        Map<String, Object> properties = kafkaProp.getConsumer().buildProperties(new DefaultSslBundleRegistry());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        init(properties);
    }

    @Override
    public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) throws Exception {
        logger.info("receive message---------->:\n{}", new String(consumerRecord.value()));
        byte[] value = consumerRecord.value();
        T t = JsonUtil.OBJECT_MAPPER.readValue(value, clazz);
        if (consumer == null) {
            logger.warn("receiver[{}] consumer[{}] is null、discard message", name, this.clazz.getName());
        }else{
            consumer.accept(t);
        }
    }
}
