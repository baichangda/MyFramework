package cn.bcd.lib.spring.kafka;

import cn.bcd.lib.base.util.DateZoneUtil;
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
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(properties);
        return new KafkaTemplate<>(producerFactory);
    }

    public static KafkaTemplate<String, byte[]> newKafkaTemplate_string_bytes(Map<String, Object> properties) {
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        DefaultKafkaProducerFactory<String, byte[]> producerFactory = new DefaultKafkaProducerFactory<>(properties);
        return new KafkaTemplate<>(producerFactory);
    }

    public static KafkaProducer<String, String> newKafkaProducer_string_string(Map<String, Object> properties) {
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(properties);
    }

    public static KafkaProducer<String, byte[]> newKafkaProducer_string_bytes(Map<String, Object> properties) {
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaProducer<>(properties);
    }


    public static KafkaConsumer<String, String> newKafkaConsumer_string_string(Map<String, Object> properties) {
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    public static KafkaConsumer<String, byte[]> newKafkaConsumer_string_bytes(Map<String, Object> properties) {
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new KafkaConsumer<>(properties);
    }


    public static void consumerSeekToBeginning(KafkaConsumer<String, byte[]> consumer) {
        logger.info("start consumer seekToBeginning");
        Set<TopicPartition> assigment = new HashSet<>();
        while (assigment.isEmpty()) {
            consumer.poll(Duration.ofSeconds(1));
            assigment = consumer.assignment();
        }
        consumer.seekToBeginning(assigment);
        logger.info("finish consumer seekToBeginning");
    }

    public static void consumerSeekToTimestamp(KafkaConsumer<String, byte[]> consumer, long seekTimestamp) {
        logger.info("start consumer seekToTimestamp[{}]", DateZoneUtil.dateToStr_yyyyMMddHHmmss(new Date(seekTimestamp)));
        Set<TopicPartition> assigment = new HashSet<>();
        while (assigment.isEmpty()) {
            consumer.poll(Duration.ofSeconds(1));
            assigment = consumer.assignment();
        }
        Map<TopicPartition, Long> partition_seekTimestamp = new HashMap<>();
        for (TopicPartition partition : assigment) {
            partition_seekTimestamp.put(partition, seekTimestamp);
        }
        Map<TopicPartition, OffsetAndTimestamp> partition_offset = consumer.offsetsForTimes(partition_seekTimestamp);
        for (TopicPartition partition : assigment) {
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
            }
        }
        logger.info("finish consumer seekToTimestamp[{}]", DateZoneUtil.dateToStr_yyyyMMddHHmmss(new Date(seekTimestamp)));
    }

}
