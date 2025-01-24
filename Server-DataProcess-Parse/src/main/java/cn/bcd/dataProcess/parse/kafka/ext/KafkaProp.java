package cn.bcd.dataProcess.parse.kafka.ext;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author liqi
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.kafka")
public class KafkaProp {

    public ConsumerProp consumer;

    public ProducerProp producer;
}
