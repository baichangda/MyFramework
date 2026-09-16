package cn.bcd.lib.spring.kafka;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.lib.spring.kafka.ext.datadriven.WorkHandler;
import cn.bcd.lib.spring.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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
                "data-test", 1, 0, true, 0,
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

    @Test
    void consumersRouteNullKeysWithoutFailing() {
        var threadConsumer = new ThreadDrivenKafkaConsumer(
                "thread-null-key", true, 2, 1, 0,
                true, 0, 0, ConsumerParam.get_singleConsumer("topic")
        ) {
            @Override
            public int index(ConsumerRecord<String, byte[]> consumerRecord) {
                return super.index(consumerRecord);
            }

            @Override
            public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) {
            }
        };
        DataDrivenKafkaConsumer dataConsumer = newDataConsumer("data-null-key");
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("topic", 0, 0, null, new byte[0]);

        try {
            assertEquals(0, threadConsumer.index(record));
            assertSame(dataConsumer.workExecutors[0], dataConsumer.getWorkExecutor(null));
        } finally {
            threadConsumer.close();
            dataConsumer.close();
        }
    }

    @Test
    void dataDrivenConsumerCanCloseFromItsWorkerThread() {
        DataDrivenKafkaConsumer consumer = newDataConsumer("data-worker-close");

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Future<?> closeTask = consumer.workExecutors[0].submit(consumer::close);
            closeTask.get();
            consumer.close();
        });
    }

    private static DataDrivenKafkaConsumer newDataConsumer(String name) {
        return new DataDrivenKafkaConsumer(
                name, 1, 0, true, 0,
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
    }
}
