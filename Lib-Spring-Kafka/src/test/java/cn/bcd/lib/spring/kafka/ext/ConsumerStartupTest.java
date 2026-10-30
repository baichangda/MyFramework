package cn.bcd.lib.spring.kafka.ext;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerStartupTest {
    @Test
    void preparedStartupCanBeClosedBeforeStartAndRepeatedCloseIsSafe() {
        AtomicInteger failures = new AtomicInteger();
        ConsumerParam param = ConsumerParam.get_singleConsumer("topic");
        try (var startup = new ConsumerStartup("unstarted-test", Map.of(), param,
                consumer -> fail("Closed startup must not consume"), failures::incrementAndGet)) {
            startup.close();
            startup.close();
            assertThrows(ExecutionException.class, () -> startup.start().get(1, TimeUnit.SECONDS));
            assertEquals(1, failures.get());
            for (Thread thread : startup.threads()) {
                assertEquals(Thread.State.NEW, thread.getState());
            }
        }
    }

    @Test
    void oneParameterCanPrepareIndependentStartups() throws Exception {
        ConsumerParam param = ConsumerParam.get_singleConsumer("topic");
        Map<String, Object> properties = Map.of("bootstrap.servers", "127.0.0.1:1",
                "group.id", "independent-startups", "enable.auto.commit", false);
        CountDownLatch release = new CountDownLatch(1);
        Consumer<KafkaConsumer<String, byte[]>> consume = consumer -> {
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        try (var first = new ConsumerStartup("first-test", properties, param, consume, () -> {});
             var second = new ConsumerStartup("second-test", properties, param, consume, () -> {})) {
            first.start().get(10, TimeUnit.SECONDS);
            second.start().get(10, TimeUnit.SECONDS);
            assertNotSame(first.threads().getFirst(), second.threads().getFirst());
            first.close();
            assertFalse(first.threads().getFirst().isAlive());
            assertTrue(second.threads().getFirst().isAlive());
        } finally {
            release.countDown();
        }
    }

    @Test
    void splitSnapshotsTopicsPartitionsAndSeekPosition() {
        String[] topics = {"first", "second"};
        ConsumerParam single = ConsumerParam.get_singleConsumer_subscribeTopics(topics).seekToTimestamp(123);
        ConsumerParam multiple = ConsumerParam.get_multipleConsumer_subscribeTopics(topics).seekToEnd();
        var singleSnapshot = ConsumerStartup.split(single);
        var multipleSnapshot = ConsumerStartup.split(multiple);
        topics[0] = "changed";
        single.seekToBeginning();
        multiple.seekToBeginning();
        assertArrayEquals(new String[]{"first", "second"}, singleSnapshot.getFirst().topics);
        assertEquals(123, singleSnapshot.getFirst().seekTimestamp);
        assertEquals(2, multipleSnapshot.size());
        assertArrayEquals(new String[]{"first"}, multipleSnapshot.getFirst().topics);
        assertArrayEquals(new String[]{"second"}, multipleSnapshot.getLast().topics);
        assertEquals(-3, multipleSnapshot.getFirst().seekTimestamp);

        TopicPartition first = new TopicPartition("topic", 0);
        TopicPartition second = new TopicPartition("topic", 1);
        TopicPartition[] partitions = {first, second};
        var assigned = ConsumerStartup.split(ConsumerParam.get_singleConsumer_assignTopicPartitions(partitions));
        var separatelyAssigned = ConsumerStartup.split(ConsumerParam.get_multipleConsumer_assignTopicPartitions(partitions));
        partitions[0] = new TopicPartition("changed", 0);
        assertArrayEquals(new TopicPartition[]{first, second}, assigned.getFirst().topicPartitions);
        assertArrayEquals(new TopicPartition[]{first}, separatelyAssigned.getFirst().topicPartitions);
        assertArrayEquals(new TopicPartition[]{second}, separatelyAssigned.getLast().topicPartitions);
    }

    @Test
    void emptyMultiConsumerModesFailBeforeStartingThreads() {
        assertThrows(IllegalArgumentException.class,
                () -> ConsumerStartup.split(ConsumerParam.get_multipleConsumer_subscribeTopics()));
        assertThrows(IllegalArgumentException.class,
                () -> ConsumerStartup.split(ConsumerParam.get_multipleConsumer_assignTopicPartitions()));
    }
}
