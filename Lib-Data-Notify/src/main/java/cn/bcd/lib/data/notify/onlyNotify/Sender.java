package cn.bcd.lib.data.notify.onlyNotify;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.kafka.KafkaUtil;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;

public class Sender<T> {
    public String topic;
    public KafkaProducer<String,byte[]> producer;

    public Sender(String topic, KafkaProperties kafkaProp) {
        this.topic = topic;
        this.producer = KafkaUtil.newKafkaProducer_string_bytes(kafkaProp.getProducer().buildProperties(new DefaultSslBundleRegistry()));
    }

    public void send(T data){
        producer.send(new ProducerRecord<>(topic, JsonUtil.toJsonAsBytes(data)));
    }
}
