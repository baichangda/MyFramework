package cn.bcd.lib.base.kafka;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class KafkaConfig {
    @Bean(name = "string_bytes_kafkaTemplate")
    public KafkaTemplate<String, byte[]> string_bytes_kafkaTemplate(KafkaProperties kafkaProp) {
        return KafkaUtil.newKafkaTemplate_string_bytes(kafkaProp.getProducer());
    }

    @Bean(name = "string_string_kafkaTemplate")
    public KafkaTemplate<String, String> string_string_kafkaTemplate(KafkaProperties kafkaProp) {
        return KafkaUtil.newKafkaTemplate_string_string(kafkaProp.getProducer());
    }
}
