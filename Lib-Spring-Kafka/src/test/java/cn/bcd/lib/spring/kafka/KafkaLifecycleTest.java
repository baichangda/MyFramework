package cn.bcd.lib.spring.kafka;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.lib.spring.kafka.ext.datadriven.WorkHandler;
import cn.bcd.lib.spring.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static cn.bcd.lib.spring.kafka.ConsumerTestUtil.awaitClosed;

class KafkaLifecycleTest {

    @Test
    void consumerSeekModesUseDocumentedSentinelValues() {
        assertEquals(-1, ConsumerParam.get_singleConsumer("topic").seekTimestamp);
        assertEquals(-2, ConsumerParam.get_singleConsumer("topic").seekToBeginning().seekTimestamp);
        assertEquals(-3, ConsumerParam.get_singleConsumer("topic").seekToEnd().seekTimestamp);
    }

    @Test
    void seekToEndResolvesOffsetBeforeReturning() {
        var partition = new TopicPartition("topic", 0);
        class LazySeekConsumer extends KafkaConsumer<String, byte[]> {
            long endOffset = 5;
            long offset;
            boolean pendingSeek;

            LazySeekConsumer() {
                super(Map.of("bootstrap.servers", "localhost:9092"),
                        new StringDeserializer(),
                        new ByteArrayDeserializer());
            }

            @Override
            public Set<TopicPartition> assignment() {
                return Set.of(partition);
            }

            @Override
            public ConsumerRecords<String, byte[]> poll(Duration timeout) {
                return ConsumerRecords.empty();
            }

            @Override
            public void seekToEnd(Collection<TopicPartition> partitions) {
                pendingSeek = true;
            }

            @Override
            public long position(TopicPartition topicPartition) {
                if (pendingSeek) {
                    offset = endOffset;
                    pendingSeek = false;
                }
                return offset;
            }
        }

        try (LazySeekConsumer consumer = new LazySeekConsumer()) {
            KafkaUtil.consumerSeekToEnd(consumer);
            consumer.endOffset++;

            assertEquals(5, consumer.position(partition),
                    "Messages published after startup must not be skipped by a deferred seek");
        }
    }

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
                "thread-test", 1, 0,
                true, 0, 0, ConsumerParam.get_singleConsumer("topic")
        ) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> consumerRecord, SingleThreadEventExecutor executor) {
            }
        };

        assertDoesNotThrow(consumer::close);
        assertThrows(IllegalStateException.class, () -> consumer.startConsume(Map.of()));
    }

    @Test
    void dataDrivenConsumerRoutesNullKeysWithoutFailing() {
        DataDrivenKafkaConsumer dataConsumer = newDataConsumer("data-null-key");
        try {
            assertSame(dataConsumer.workExecutors[0], dataConsumer.getWorkExecutor(null));
        } finally {
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

    @Test
    void dataDrivenConsumerCanGetHandlerFromItsWorkerThread() {
        DataDrivenKafkaConsumer consumer = newDataConsumer("data-worker-get-handler");
        WorkHandler handler = new WorkHandler("id") {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) {
            }
        };

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    consumer.workExecutors[0].submit(() -> {
                        consumer.workExecutors[0].workHandlers.put(handler.id, handler);
                        assertSame(handler, consumer.getHandler(handler.id));
                    }).get());
        } finally {
            consumer.close();
        }
    }

    @Test
    void dataDrivenConsumerCanCloseAfterAsynchronousInitializationFailure() {
        DataDrivenKafkaConsumer consumer = newDataConsumer("data-startup-result");

        try {
            assertDoesNotThrow(() -> consumer.startConsume(Map.of()));
            consumer.close();
            awaitClosed(consumer);
            assertTrue(consumer.workExecutors[0].isTerminated());
        } finally {
            consumer.close();
        }
    }

    @Test
    void scannerDoesNotRemoveHandlerRefreshedAfterScanStarts() {
        DataDrivenKafkaConsumer consumer = newDataConsumer("data-scanner-race");
        var executor = consumer.workExecutors[0];
        CountDownLatch workerEntered = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicReference<WorkHandler> activeHandler = new AtomicReference<>();

        try {
            executor.submit(() -> executor.workHandlers.put("id", newTestHandler("id"))).get();
            executor.execute(() -> {
                workerEntered.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(workerEntered.await(5, TimeUnit.SECONDS));

            consumer.scanAndDestroyWorkHandler(1);
            Future<?> messageTask = executor.submit(() -> {
                WorkHandler handler = executor.workHandlers.computeIfAbsent("id", KafkaLifecycleTest::newTestHandler);
                handler.lastMessageTime = Long.MAX_VALUE;
                activeHandler.set(handler);
            });
            releaseWorker.countDown();
            messageTask.get();
            executor.submit(() -> {
            }).get();

            assertSame(activeHandler.get(), consumer.getHandler("id"));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            releaseWorker.countDown();
            consumer.close();
        }
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

    private static WorkHandler newTestHandler(String id) {
        return new WorkHandler(id) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) {
            }
        };
    }
}
