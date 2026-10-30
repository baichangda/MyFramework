package cn.bcd.lib.spring.kafka.ext.threaddriven;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.ConsumerGroup;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ThreadDrivenStartupTest {
    private static final Map<String, Object> PROPERTIES = Map.of(
            "bootstrap.servers", "127.0.0.1:1", "group.id", "startup-test",
            "enable.auto.commit", false);

    @Test
    void allFourModesInitializeAndRepeatedStartReturnsTheSameFuture() throws Exception {
        var partition = new TopicPartition("topic", 0);
        ConsumerParam[] modes = {
                ConsumerParam.get_singleConsumer_subscribeTopics("topic", "other"),
                ConsumerParam.get_multipleConsumer_subscribeTopics("topic", "other"),
                ConsumerParam.get_singleConsumer_assignTopicPartitions(partition),
                ConsumerParam.get_multipleConsumer_assignTopicPartitions(
                        partition, new TopicPartition("topic", 1))
        };
        for (ConsumerParam mode : modes) {
            ThreadDrivenKafkaConsumer consumer = newConsumer(mode);
            Thread[] threads;
            try (consumer) {
                var started = consumer.startConsume(PROPERTIES);
                assertSame(started, consumer.startConsume(PROPERTIES));
                started.get(10, TimeUnit.SECONDS);
                assertSame(started, consumer.startConsume(PROPERTIES));
                threads = consumerThreads(consumer);
                assertEquals(mode.mode == 2 || mode.mode == 4 ? 2 : 1, threads.length);
            }
            for (Thread thread : threads) {
                assertFalse(thread.isAlive());
            }
        }
    }

    @Test
    void partialStartupFailureStopsEveryThreadBeforeReportingFailureAndAllowsRetry() throws Exception {
        ConsumerParam param = ConsumerParam.get_multipleConsumer_subscribeTopics("valid", "").seekToBeginning();
        try (ThreadDrivenKafkaConsumer consumer = newConsumer(param)) {
            var failed = consumer.startConsume(PROPERTIES);
            assertThrows(ExecutionException.class, () -> failed.get(10, TimeUnit.SECONDS));
            Thread[] failedThreads = consumerThreads(consumer);
            for (Thread thread : failedThreads) {
                assertFalse(thread.isAlive(), "Failure must be published after cleanup");
            }

            param.topics[1] = "other";
            param.seekTimestamp = -1;
            var retry = consumer.startConsume(PROPERTIES);
            assertNotSame(failed, retry);
            retry.get(10, TimeUnit.SECONDS);
            assertTrue(consumer.running_consume, "Old threads must not stop the new attempt");
        }
    }

    @Test
    void closeDuringInitializationCompletesStartupAndStopsAllThreads() throws Exception {
        try (ThreadDrivenKafkaConsumer consumer = newConsumer(
                ConsumerParam.get_singleConsumer("topic").seekToBeginning())) {
            CompletableFuture<Void> started = consumer.startConsume(PROPERTIES);
            CompletableFuture.runAsync(consumer::close).get(10, TimeUnit.SECONDS);
            assertTrue(started.isDone());
            assertThrows(ExecutionException.class, () -> started.get(1, TimeUnit.SECONDS));
            for (Thread thread : consumerThreads(consumer)) {
                assertFalse(thread.isAlive());
            }
        }
    }

    @Test
    void startupCallbackCanCloseConsumerWithoutWaitingForItself() throws Exception {
        try (ThreadDrivenKafkaConsumer consumer = newConsumer(ConsumerParam.get_singleConsumer("topic"))) {
            consumer.startConsume(PROPERTIES).thenRun(consumer::close).get(10, TimeUnit.SECONDS);
            consumer.close();
            for (Thread thread : consumerThreads(consumer)) {
                assertFalse(thread.isAlive());
            }
        }
    }

    private static Thread[] consumerThreads(ThreadDrivenKafkaConsumer consumer) throws Exception {
        var field = ThreadDrivenKafkaConsumer.class.getDeclaredField("consumerGroup");
        field.setAccessible(true);
        return ((ConsumerGroup) field.get(consumer)).threads().toArray(Thread[]::new);
    }

    private static ThreadDrivenKafkaConsumer newConsumer(ConsumerParam param) {
        return new ThreadDrivenKafkaConsumer("thread-startup-test", 1, 1000, true, 0, 0, param) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record, SingleThreadEventExecutor executor) {
                throw new AssertionError("No records expected without a broker");
            }
        };
    }
}
