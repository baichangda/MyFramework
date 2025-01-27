package cn.bcd.base.kafka.ext;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;

public class ProducerFactory {

    final ThreadLocal<Producer<String, byte[]>> producers = new ThreadLocal<>();

    final KafkaProperties.Producer producerProp;

    public ProducerFactory(KafkaProperties.Producer producerProp) {
        this.producerProp=producerProp;
    }

    public static Producer<String, byte[]> newProducer(KafkaProperties.Producer producerProp) {
        return new KafkaProducer<>(producerProp.buildProperties(new DefaultSslBundleRegistry()));
    }

    /**
     * 获取producer并绑定到线程上
     */
    public Producer<String, byte[]> getProducerInThreadLocal() {
        Producer<String, byte[]> producer = producers.get();
        if (producer == null) {
            producer = newProducer(producerProp);
            producers.set(producer);
        }
        return producer;
    }
}
