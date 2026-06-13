package cn.bcd.lib.spring.kafka;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.lib.spring.kafka.ext.datadriven.WorkHandler;
import cn.bcd.lib.spring.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaLifecycleTest {

    @Test
    void kafkaUtilDoesNotMutateImmutableProperties() {
        Map<String, Object> properties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"
        );

        try (KafkaProducer<String, byte[]> ignored = KafkaUtil.newKafkaProducer_string_bytes(properties)) {
            assertEquals(1, properties.size());
        }
    }

    @Test
    void dataDrivenConsumerCanCloseBeforeStart() {
        DataDrivenKafkaConsumer consumer = new DataDrivenKafkaConsumer(
                "data-test", 1, false, 0, true, 0,
                null, 0, ConsumerParam.get_singleConsumer("topic")
        ) {
            @Override
            public WorkHandler newHandler(String id, ConsumerRecord<String, byte[]> first) {
                return new WorkHandler(id) {
                    @Override
                    public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) {
                    }
                };
            }
        };

        assertDoesNotThrow(consumer::close);
        assertThrows(IllegalStateException.class, () -> consumer.startConsume(Map.of()));
    }

    @Test
    void threadDrivenConsumerCanCloseBeforeStart() {
        ThreadDrivenKafkaConsumer consumer = new ThreadDrivenKafkaConsumer(
                "thread-test", false, 1, 1, 0,
                true, 0, 0, ConsumerParam.get_singleConsumer("topic")
        ) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) {
            }
        };

        assertDoesNotThrow(consumer::close);
        assertThrows(IllegalStateException.class, () -> consumer.startConsume(Map.of()));
    }
}
