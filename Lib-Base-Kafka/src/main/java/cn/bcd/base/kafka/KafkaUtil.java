package cn.bcd.base.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

public class KafkaUtil {
    public static KafkaTemplate<String, String> newKafkaTemplate_string_string(KafkaProperties.Producer producerProp) {
        Map<String, Object> properties = producerProp.buildProperties(new DefaultSslBundleRegistry());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(properties);
        return new KafkaTemplate<>(producerFactory);
    }

    public static KafkaTemplate<String, byte[]> newKafkaTemplate_string_bytes(KafkaProperties.Producer producerProp) {
        Map<String, Object> properties = producerProp.buildProperties(new DefaultSslBundleRegistry());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        DefaultKafkaProducerFactory<String, byte[]> producerFactory = new DefaultKafkaProducerFactory<>(properties);
        return new KafkaTemplate<>(producerFactory);
    }

    public static KafkaProducer<String, String> newKafkaProducer_string_string(KafkaProperties.Producer producerProp) {
        Map<String, Object> properties = producerProp.buildProperties(new DefaultSslBundleRegistry());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(properties);
    }

    public static KafkaProducer<String, byte[]> newKafkaProducer_string_bytes(KafkaProperties.Producer producerProp) {
        Map<String, Object> properties = producerProp.buildProperties(new DefaultSslBundleRegistry());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaProducer<>(properties);
    }


    public static KafkaConsumer<String, String> newKafkaConsumer_string_string(KafkaProperties.Consumer consumerProp) {
        Map<String, Object> properties = consumerProp.buildProperties(new DefaultSslBundleRegistry());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    public static KafkaConsumer<String, byte[]> newKafkaConsumer_string_bytes(KafkaProperties.Consumer consumerProp) {
        Map<String, Object> properties = consumerProp.buildProperties(new DefaultSslBundleRegistry());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new KafkaConsumer<>(properties);
    }
}
