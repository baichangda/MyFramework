package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class DataDrivenConsumerLoopTest {

    @Test
    void backpressureKeepsPollingAndResumesWithNormalTimeout() throws Exception {
        try (DataDrivenKafkaConsumer consumer = new DataDrivenKafkaConsumer(
                "data-loop-test", 1, 1000, true, 0, null, 0,
                ConsumerParam.get_singleConsumer("topic")) {
            @Override
            public WorkHandler newHandler(String id, ConsumerRecord<String, byte[]> first) {
                throw new AssertionError("No records should be dispatched");
            }
        }; StubKafkaConsumer kafka = new StubKafkaConsumer()) {
            consumer.blockingNum.add(1000);
            kafka.pollAction = timeout -> {
                if (++kafka.pollCount == 1) {
                    assertEquals(Duration.ofMillis(100), timeout);
                    assertEquals(kafka.assignment(), kafka.paused());
                    consumer.blockingNum.add(-1000);
                } else {
                    assertEquals(2, kafka.pollCount);
                    assertEquals(Duration.ofSeconds(1), timeout);
                    assertTrue(kafka.paused().isEmpty());
                    consumer.running_consume = false;
                }
                return ConsumerRecords.empty();
            };
            runLoop(consumer, kafka);
            assertEquals(2, kafka.pollCount);
            assertEquals(0, kafka.closeCount);
        }
    }

    private static void runLoop(DataDrivenKafkaConsumer consumer, KafkaConsumer<String, byte[]> kafka) {
        consumer.running_consume = true;
        consumer.consume(kafka);
    }

    private static class StubKafkaConsumer extends KafkaConsumer<String, byte[]> {
        final TopicPartition partition = new TopicPartition("topic", 0);
        final Set<TopicPartition> paused = new HashSet<>();
        Function<Duration, ConsumerRecords<String, byte[]>> pollAction;
        int pollCount;
        int closeCount;

        StubKafkaConsumer() {
            super(Map.of("bootstrap.servers", "localhost:9092"),
                    new StringDeserializer(), new ByteArrayDeserializer());
        }

        @Override
        public Set<TopicPartition> assignment() {
            return Set.of(partition);
        }

        @Override
        public Set<TopicPartition> paused() {
            return Set.copyOf(paused);
        }

        @Override
        public void pause(Collection<TopicPartition> partitions) {
            paused.addAll(partitions);
        }

        @Override
        public void resume(Collection<TopicPartition> partitions) {
            paused.removeAll(partitions);
        }

        @Override
        public ConsumerRecords<String, byte[]> poll(Duration timeout) {
            return pollAction.apply(timeout);
        }

        @Override
        public void close() {
            closeCount++;
            super.close();
        }
    }
}
