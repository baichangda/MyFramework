package cn.bcd.lib.spring.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.NoOffsetForPartitionException;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class KafkaUtilTest {
    private static final TopicPartition FIRST = new TopicPartition("topic", 0);
    private static final TopicPartition SECOND = new TopicPartition("topic", 1);
    private static final List<TopicPartition> PARTITIONS = List.of(FIRST, SECOND);
    private static final long TIMESTAMP = 123;
    private static final List<Consumer<SeekConsumer>> SEEK_OPERATIONS = List.of(
            KafkaUtil::consumerSeekToBeginning,
            KafkaUtil::consumerSeekToEnd,
            consumer -> KafkaUtil.consumerSeekToTimestamp(consumer, TIMESTAMP));

    @Test
    void alreadyAssignedPartitionsCanSeekWithoutPollingUninitializedOffsets() {
        for (Consumer<SeekConsumer> seek : SEEK_OPERATIONS) {
            try (SeekConsumer consumer = new SeekConsumer(0)) {
                assertDoesNotThrow(() -> seek.accept(consumer));
                assertEquals(0, consumer.pollCount, "Assigned partitions must be positioned before any poll");
            }
        }
    }

    @Test
    void unassignedConsumerStillPollsUntilPartitionsAreAvailable() {
        for (Consumer<SeekConsumer> seek : SEEK_OPERATIONS) {
            try (SeekConsumer consumer = new SeekConsumer(2)) {
                seek.accept(consumer);
                assertEquals(2, consumer.pollCount);
                assertEquals(Set.copyOf(PARTITIONS), consumer.assignment());
            }
        }
    }

    @Test
    void timestampFallbackResolvesEndBeforeReturningWithoutChangingMatchedOffsets() {
        try (SeekConsumer consumer = new SeekConsumer(1)) {
            KafkaUtil.consumerSeekToTimestamp(consumer, TIMESTAMP);
            assertEquals(Map.of(FIRST, TIMESTAMP, SECOND, TIMESTAMP), consumer.requestedTimestamps);

            // 模拟方法返回后、业务首次 poll 前有新消息写入；不得把新消息也跳过。
            consumer.endOffsets.put(SECOND, 21L);
            assertEquals(20, consumer.position(SECOND));
            assertEquals(3, consumer.position(FIRST), "Matching timestamp offset must be kept");
            assertTrue(consumer.pendingEndSeeks.isEmpty());
        }
    }

    /** 模拟 Kafka 的惰性 seek，避免单元测试依赖实际 Kafka 服务。 */
    private static class SeekConsumer extends KafkaConsumer<String, byte[]> {
        final int assignAfterPolls;
        final Map<TopicPartition, Long> positions = new HashMap<>();
        final Map<TopicPartition, Long> endOffsets = new HashMap<>(Map.of(FIRST, 10L, SECOND, 20L));
        final Set<TopicPartition> pendingEndSeeks = new HashSet<>();
        Map<TopicPartition, Long> requestedTimestamps;
        int pollCount;

        SeekConsumer(int assignAfterPolls) {
            super(Map.of("bootstrap.servers", "127.0.0.1:1", "auto.offset.reset", "none",
                            "enable.auto.commit", false),
                    new StringDeserializer(), new ByteArrayDeserializer());
            this.assignAfterPolls = assignAfterPolls;
            if (assignAfterPolls == 0) {
                assign(PARTITIONS);
            }
        }

        @Override
        public ConsumerRecords<String, byte[]> poll(Duration timeout) {
            pollCount++;
            if (assignAfterPolls == 0) {
                // 已 assign 但没有起始位点，模拟 reset=none 时先 poll 的失败行为。
                throw new NoOffsetForPartitionException(Set.copyOf(PARTITIONS));
            }
            if (pollCount == assignAfterPolls) {
                assign(PARTITIONS);
            }
            return ConsumerRecords.empty();
        }

        @Override
        public void seekToBeginning(Collection<TopicPartition> partitions) {
            for (TopicPartition partition : partitions) {
                positions.put(partition, 0L);
            }
        }

        @Override
        public void seekToEnd(Collection<TopicPartition> partitions) {
            pendingEndSeeks.addAll(partitions);
        }

        @Override
        public long position(TopicPartition partition) {
            if (pendingEndSeeks.remove(partition)) {
                positions.put(partition, endOffsets.get(partition));
            }
            return positions.get(partition);
        }

        @Override
        public void seek(TopicPartition partition, long offset) {
            positions.put(partition, offset);
        }

        @Override
        public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> timestamps) {
            requestedTimestamps = Map.copyOf(timestamps);
            Map<TopicPartition, OffsetAndTimestamp> offsets = new HashMap<>();
            offsets.put(FIRST, new OffsetAndTimestamp(3, TIMESTAMP));
            offsets.put(SECOND, null);
            return offsets;
        }
    }
}
