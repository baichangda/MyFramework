package cn.bcd.lib.spring.kafka.ext.threaddriven;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;

import io.netty.util.concurrent.SingleThreadEventExecutor;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ThreadDrivenConsumerLoopTest {

    @Test
    void pausedConsumerKeepsPollingAndResumesUsingItsActualState() throws Exception {
        try (ThreadDrivenKafkaConsumer consumer = newConsumer(0, 0);
             StubKafkaConsumer kafka = new StubKafkaConsumer()) {
            consumer.pauseConsume();
            kafka.pollAction = timeout -> {
                if (++kafka.pollCount == 1) {
                    assertEquals(Duration.ofMillis(100), timeout);
                    assertEquals(kafka.assignment(), kafka.paused());
                    consumer.resumeConsume();
                } else {
                    assertEquals(2, kafka.pollCount);
                    assertEquals(Duration.ofSeconds(3), timeout);
                    assertTrue(kafka.paused().isEmpty());
                    consumer.running_consume = false;
                }
                return ConsumerRecords.empty();
            };
            runLoop(consumer, kafka);
            assertEquals(2, kafka.pollCount);
            assertEquals(0, kafka.closeCount, "KafkaConsumerGroup owns the native consumer");
        }
    }

    @Test
    void shutdownExceptionExitsWithoutRetryingOrClosingTheGroupOwnedConsumer() throws Exception {
        try (ThreadDrivenKafkaConsumer consumer = newConsumer(0, 0);
             StubKafkaConsumer kafka = new StubKafkaConsumer()) {
            kafka.pollAction = timeout -> {
                consumer.running_consume = false;
                throw new IllegalStateException("consumer stopped");
            };
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> runLoop(consumer, kafka));
            assertEquals(0, kafka.closeCount);
        }
    }

    @Test
    void rateResetAndMonitoringContinueWhileWorkerIsBlocked() throws Exception {
        CountDownLatch monitorTick = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ThreadDrivenKafkaConsumer consumer = new ThreadDrivenKafkaConsumer(
                "schedule-test", 1, 1000, true, 10, 1, ConsumerParam.get_singleConsumer("topic")) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record, SingleThreadEventExecutor executor) {
            }

            @Override
            public String monitor_log() {
                monitorTick.countDown();
                return super.monitor_log();
            }
        }) {
            consumer.consumeCount.set(10);
            consumer.workPool[0].submit(() -> {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertTrue(monitorTick.await(5, TimeUnit.SECONDS));
                assertEquals(0, consumer.consumeCount.get());
            } finally {
                release.countDown();
            }
        }
    }

    private static ThreadDrivenKafkaConsumer newConsumer(int speed, int monitorPeriod) {
        return new ThreadDrivenKafkaConsumer("loop-test", 1, 1000, true, speed, monitorPeriod,
                ConsumerParam.get_singleConsumer("topic")) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record, SingleThreadEventExecutor executor) {
            }
        };
    }

    private static void runLoop(ThreadDrivenKafkaConsumer consumer, KafkaConsumer<String, byte[]> kafka)
            throws Exception {
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
