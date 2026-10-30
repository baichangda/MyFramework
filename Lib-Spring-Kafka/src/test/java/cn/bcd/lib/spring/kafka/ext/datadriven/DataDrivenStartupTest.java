package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.spring.kafka.ext.ConsumerGroup;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DataDrivenStartupTest {
    private static final Map<String, Object> PROPERTIES = Map.of(
            "bootstrap.servers", "127.0.0.1:1", "group.id", "startup-test",
            "enable.auto.commit", false);

    @Test
    void allFourModesStartAndRepeatedStartDoesNotCreateMoreThreads() throws Exception {
        var partition = new TopicPartition("topic", 0);
        ConsumerParam[] modes = {
                ConsumerParam.get_singleConsumer_subscribeTopics("topic", "other"),
                ConsumerParam.get_multipleConsumer_subscribeTopics("topic", "other"),
                ConsumerParam.get_singleConsumer_assignTopicPartitions(partition),
                ConsumerParam.get_multipleConsumer_assignTopicPartitions(
                        partition, new TopicPartition("topic", 1))
        };
        for (ConsumerParam mode : modes) {
            TestConsumer consumer = new TestConsumer(mode, false, false);
            Thread[] threads;
            try (consumer) {
                consumer.startConsume(PROPERTIES);
                threads = consumerThreads(consumer);
                consumer.startConsume(PROPERTIES);
                assertArrayEquals(threads, consumerThreads(consumer));
                assertTrue(consumer.initialized.await(10, TimeUnit.SECONDS));
                assertEquals(mode.mode == 2 || mode.mode == 4 ? 2 : 1, threads.length);
            }
            for (Thread thread : threads) {
                assertFalse(thread.isAlive());
            }
        }
    }

    @Test
    void initializationFailureOnlyStopsItsOwnThread() throws Exception {
        ConsumerParam param = ConsumerParam.get_multipleConsumer_subscribeTopics("valid", "");
        try (TestConsumer consumer = new TestConsumer(param, false, false)) {
            assertDoesNotThrow(() -> consumer.startConsume(PROPERTIES));
            Thread[] threads = consumerThreads(consumer);
            threads[1].join(10000);
            assertFalse(threads[1].isAlive());
            assertTrue(consumer.anyConsumerEntered.await(10, TimeUnit.SECONDS));
            assertTrue(threads[0].isAlive());
            assertTrue(consumer.running_consume);

            consumer.startConsume(PROPERTIES);
            assertArrayEquals(threads, consumerThreads(consumer), "Repeated start must not silently rebuild failed threads");
        }
    }

    @Test
    void exitingOnePollLoopDoesNotStopOtherConsumers() throws Exception {
        ConsumerParam param = ConsumerParam.get_multipleConsumer_subscribeTopics("exit", "healthy");
        try (TestConsumer consumer = new TestConsumer(param, true, false)) {
            consumer.startConsume(PROPERTIES);
            assertTrue(consumer.initialized.await(10, TimeUnit.SECONDS));
            Thread[] threads = consumerThreads(consumer);
            threads[0].join(10000);
            assertFalse(threads[0].isAlive());
            assertTrue(threads[1].isAlive());
            assertTrue(consumer.running_consume);
        }
    }

    @Test
    void closeDuringInitializationStopsAllThreads() throws Exception {
        try (TestConsumer consumer = new TestConsumer(
                ConsumerParam.get_singleConsumer("topic").seekToBeginning(), false, false)) {
            consumer.startConsume(PROPERTIES);
            CompletableFuture.runAsync(consumer::close).get(10, TimeUnit.SECONDS);
            for (Thread thread : consumerThreads(consumer)) {
                assertFalse(thread.isAlive());
            }
        }
    }

    @Test
    void consumerThreadCanCloseWithoutWaitingForItself() throws Exception {
        try (TestConsumer consumer = new TestConsumer(ConsumerParam.get_singleConsumer("topic"), false, true)) {
            consumer.startConsume(PROPERTIES);
            assertTrue(consumer.anyConsumerEntered.await(10, TimeUnit.SECONDS));
            CompletableFuture.runAsync(consumer::close).get(10, TimeUnit.SECONDS);
            for (Thread thread : consumerThreads(consumer)) {
                assertFalse(thread.isAlive());
            }
        }
    }

    private static Thread[] consumerThreads(DataDrivenKafkaConsumer consumer) throws Exception {
        var field = DataDrivenKafkaConsumer.class.getDeclaredField("consumerGroup");
        field.setAccessible(true);
        return ((ConsumerGroup) field.get(consumer)).threads().toArray(Thread[]::new);
    }

    private static class TestConsumer extends DataDrivenKafkaConsumer {
        final CountDownLatch initialized;
        final CountDownLatch anyConsumerEntered = new CountDownLatch(1);
        final boolean exitFirst;
        final boolean closeInside;

        TestConsumer(ConsumerParam param, boolean exitFirst, boolean closeInside) {
            super("data-startup-test", 1, 1000, true, 0, null, 0, param);
            int count = switch (param.mode) {
                case 2 -> param.topics.length;
                case 4 -> param.topicPartitions.length;
                default -> 1;
            };
            initialized = new CountDownLatch(count);
            this.exitFirst = exitFirst;
            this.closeInside = closeInside;
        }

        @Override
        public void consume(KafkaConsumer<String, byte[]> consumer) {
            initialized.countDown();
            anyConsumerEntered.countDown();
            if (closeInside) {
                close();
                return;
            }
            if (exitFirst && consumer.subscription().contains("exit")) {
                Thread.currentThread().interrupt();
            }
            super.consume(consumer);
        }

        @Override
        public WorkHandler newHandler(String id, ConsumerRecord<String, byte[]> first) {
            throw new AssertionError("No records expected without a broker");
        }
    }
}
