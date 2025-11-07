package cn.bcd.lib.spring.kafka;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

@Configuration
public class KafkaConfig {
    @Bean(name = "string_bytes_kafkaTemplate")
    public KafkaTemplate<String, byte[]> string_bytes_kafkaTemplate(KafkaProperties kafkaProp) {
        Map<String, Object> producerProp = kafkaProp.getProducer().buildProperties(new DefaultSslBundleRegistry());
        return KafkaUtil.newKafkaTemplate_string_bytes(producerProp);
    }

    @Bean(name = "string_string_kafkaTemplate")
    public KafkaTemplate<String, String> string_string_kafkaTemplate(KafkaProperties kafkaProp) {
        Map<String, Object> producerProp = kafkaProp.getProducer().buildProperties(new DefaultSslBundleRegistry());
        return KafkaUtil.newKafkaTemplate_string_string(producerProp);
    }
}
