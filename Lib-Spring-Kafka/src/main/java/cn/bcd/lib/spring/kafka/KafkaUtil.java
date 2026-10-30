package cn.bcd.lib.spring.kafka;

import cn.bcd.lib.base.util.DateUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.*;

public class KafkaUtil {

    static Logger logger = LoggerFactory.getLogger(KafkaUtil.class);

    public static KafkaTemplate<String, String> newKafkaTemplate_string_string(Map<String, Object> properties) {
        Map<String, Object> config = new HashMap<>(properties);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(config);
        return new KafkaTemplate<>(producerFactory) {
            @Override
            public void destroy() {
                try {
                    super.destroy();
                } finally {
                    // 工厂由当前模板独占；即使模板自身清理失败，也必须关闭工厂缓存的 Producer。
                    producerFactory.destroy();
                }
            }
        };
    }

    public static KafkaTemplate<String, byte[]> newKafkaTemplate_string_bytes(Map<String, Object> properties) {
        Map<String, Object> config = new HashMap<>(properties);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        DefaultKafkaProducerFactory<String, byte[]> producerFactory = new DefaultKafkaProducerFactory<>(config);
        return new KafkaTemplate<>(producerFactory) {
            @Override
            public void destroy() {
                try {
                    super.destroy();
                } finally {
                    // 工厂由当前模板独占；即使模板自身清理失败，也必须关闭工厂缓存的 Producer。
                    producerFactory.destroy();
                }
            }
        };
    }

    public static KafkaProducer<String, String> newKafkaProducer_string_string(Map<String, Object> properties) {
        Map<String, Object> config = new HashMap<>(properties);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(config);
    }

    public static KafkaProducer<String, byte[]> newKafkaProducer_string_bytes(Map<String, Object> properties) {
        Map<String, Object> config = new HashMap<>(properties);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaProducer<>(config);
    }


    public static KafkaConsumer<String, String> newKafkaConsumer_string_string(Map<String, Object> properties) {
        Map<String, Object> config = new HashMap<>(properties);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(config);
    }

    public static KafkaConsumer<String, byte[]> newKafkaConsumer_string_bytes(Map<String, Object> properties) {
        Map<String, Object> config = new HashMap<>(properties);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new KafkaConsumer<>(config);
    }


    public static void consumerSeekToBeginning(KafkaConsumer<String, byte[]> consumer) {
        logger.info("start consumer seekToBeginning");
        // 已 assign 的分区直接定位，避免先 poll 因尚无消费位点（例如 reset=none）而失败。
        Set<TopicPartition> assignment = consumer.assignment();
        while (assignment.isEmpty()) {
            consumer.poll(Duration.ofSeconds(1));
            assignment = consumer.assignment();
        }
        consumer.seekToBeginning(assignment);
        logger.info("finish consumer seekToBeginning");
    }

    public static void consumerSeekToEnd(KafkaConsumer<String, byte[]> consumer) {
        logger.info("start consumer seekToEnd");
        // 只有尚未获得分区时才 poll，订阅模式仍通过 poll 等待消费组分配。
        Set<TopicPartition> assignment = consumer.assignment();
        while (assignment.isEmpty()) {
            consumer.poll(Duration.ofSeconds(1));
            assignment = consumer.assignment();
        }
        consumer.seekToEnd(assignment);
        // seekToEnd 是惰性定位，立即解析，避免方法返回后新增的消息也被跳过。
        for (TopicPartition partition : assignment) {
            consumer.position(partition);
        }
        logger.info("finish consumer seekToEnd");
    }

    public static void consumerSeekToTimestamp(KafkaConsumer<String, byte[]> consumer, long seekTimestamp) {
        logger.info("start consumer seekToTimestamp[{}]", DateUtil.dateToStr_yyyyMMddHHmmss(new Date(seekTimestamp)));
        Set<TopicPartition> assignment = consumer.assignment();
        while (assignment.isEmpty()) {
            consumer.poll(Duration.ofSeconds(1));
            assignment = consumer.assignment();
        }
        Map<TopicPartition, Long> partition_seekTimestamp = new HashMap<>();
        for (TopicPartition partition : assignment) {
            partition_seekTimestamp.put(partition, seekTimestamp);
        }
        Map<TopicPartition, OffsetAndTimestamp> partition_offset = consumer.offsetsForTimes(partition_seekTimestamp);
        for (TopicPartition partition : assignment) {
            OffsetAndTimestamp offsetAndTimestamp = partition_offset.get(partition);
            if (offsetAndTimestamp != null) {
                logger.info("consumer seek topic[{}] partition[{}] offset[{}]",
                        partition.topic(),
                        partition.partition(),
                        offsetAndTimestamp.offset());
                consumer.seek(partition, offsetAndTimestamp.offset());
            } else {
                logger.info("consumer seekToEnd topic[{}] partition[{}]",
                        partition.topic(),
                        partition.partition());
                consumer.seekToEnd(Collections.singletonList(partition));
                // 回退到末尾也要在返回前固定位置，与 consumerSeekToEnd 保持一致。
                consumer.position(partition);
            }
        }
        logger.info("finish consumer seekToTimestamp[{}]", DateUtil.dateToStr_yyyyMMddHHmmss(new Date(seekTimestamp)));
    }

}
