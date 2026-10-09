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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaLifecycleTest {

    @Test
    void consumerSeekModesUseDocumentedSentinelValues() {
        assertEquals(-1, ConsumerParam.get_singleConsumer("topic").seekTimestamp);
        assertEquals(-2, ConsumerParam.get_singleConsumer("topic").seekToBeginning().seekTimestamp);
        assertEquals(-3, ConsumerParam.get_singleConsumer("topic").seekToEnd().seekTimestamp);
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
    void dataDrivenConsumerReturnsStartupFailureAndCanRetry() {
        DataDrivenKafkaConsumer consumer = newDataConsumer("data-startup-result");

        try {
            var firstResult = consumer.startConsume(Map.of());
            assertThrows(CompletionException.class, firstResult::join);

            var secondResult = consumer.startConsume(Map.of());
            assertNotSame(firstResult, secondResult);
            assertThrows(CompletionException.class, secondResult::join);
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
