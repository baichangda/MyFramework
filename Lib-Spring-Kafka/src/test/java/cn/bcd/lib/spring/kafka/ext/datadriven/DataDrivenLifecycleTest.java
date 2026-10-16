package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class DataDrivenLifecycleTest {

    @Test
    void handlerLifecycleStaysOnItsExecutorAndCreatesOnlyOnce() {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger initialized = new AtomicInteger();
        AtomicInteger messages = new AtomicInteger();
        AtomicInteger destroyed = new AtomicInteger();
        try (TestConsumer consumer = new TestConsumer(true, (id, first) -> {
            created.incrementAndGet();
            return new WorkHandler(id) {
                @Override
                public void init(ConsumerRecord<String, byte[]> first) {
                    assertTrue(executor.inEventLoop());
                    initialized.incrementAndGet();
                }

                @Override
                public void onMessage(ConsumerRecord<String, byte[]> record) {
                    assertTrue(executor.inEventLoop());
                    messages.incrementAndGet();
                }

                @Override
                public void destroy() {
                    assertTrue(executor.inEventLoop());
                    destroyed.incrementAndGet();
                }
            };
        })) {
            for (int i = 0; i < 100; i++) {
                consumer.enqueue(record("id", i));
            }
            consumer.close();
            assertEquals(1, created.get());
            assertEquals(1, initialized.get());
            assertEquals(100, messages.get());
            assertEquals(1, destroyed.get());
            assertTrue(consumer.workExecutors[0].workHandlers.isEmpty());
            assertEquals(0, consumer.blockingNum.sum());
        }
    }

    @Test
    void initializationFailureReleasesManualCountAndNextMessageRetries() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger messages = new AtomicInteger();
        try (TestConsumer consumer = new TestConsumer(false, (id, first) -> new WorkHandler(id) {
            @Override
            public void init(ConsumerRecord<String, byte[]> first) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("init failure");
                }
            }

            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record) {
                messages.incrementAndGet();
                this.consumer.blockingNum.decrement();
            }
        })) {
            consumer.enqueue(record("id", 0));
            consumer.enqueue(record("id", 1));
            consumer.close();
            assertEquals(2, attempts.get());
            assertEquals(1, messages.get());
            assertEquals(0, consumer.blockingNum.sum());
        }
    }

    @Test
    void messageFailureReleasesCountAndKeepsHandlerAvailable() throws Exception {
        AtomicInteger messages = new AtomicInteger();
        try (TestConsumer consumer = new TestConsumer(true, (id, first) -> new WorkHandler(id) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record) {
                if (messages.incrementAndGet() == 1) {
                    throw new IllegalStateException("message failure");
                }
            }
        })) {
            consumer.enqueue(record("id", 0));
            consumer.enqueue(record("id", 1));
            consumer.drain();
            assertEquals(2, messages.get());
            assertNotNull(consumer.getHandler("id"));
            assertEquals(0, consumer.blockingNum.sum());
        }
    }

    @Test
    void removingHandlerInsideItsExecutorDoesNotDeadlockOrDestroyTwice() throws Exception {
        AtomicInteger destroyed = new AtomicInteger();
        try (TestConsumer consumer = new TestConsumer(true, (id, first) -> new WorkHandler(id) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record) {
            }

            @Override
            public void destroy() {
                destroyed.incrementAndGet();
            }
        })) {
            consumer.enqueue(record("id", 0));
            consumer.drain();
            consumer.workExecutors[0].submit(() -> {
                consumer.removeHandler("id").get();
                return null;
            }).get(5, TimeUnit.SECONDS);
            assertNull(consumer.getHandler("id"));
            consumer.close();
            assertEquals(1, destroyed.get());
        }
    }

    @Test
    void scannerDestroysOnlyExpiredHandlers() throws Exception {
        ConcurrentLinkedQueue<String> destroyed = new ConcurrentLinkedQueue<>();
        try (TestConsumer consumer = new TestConsumer(true, (id, first) -> new WorkHandler(id) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record) {
            }

            @Override
            public void destroy() {
                destroyed.add(id);
            }
        })) {
            consumer.enqueue(record("old", 0));
            consumer.enqueue(record("live", 0));
            consumer.drain();
            WorkHandler live = consumer.getHandler("live");
            consumer.workExecutors[0].submit(() -> {
                consumer.workExecutors[0].workHandlers.get("old").lastMessageTime = 0;
                live.lastMessageTime = DateUtil.CacheSecond.current();
            }).get(5, TimeUnit.SECONDS);
            consumer.scanAndDestroyWorkHandler(60);
            consumer.drain();
            assertNull(consumer.getHandler("old"));
            assertSame(live, consumer.getHandler("live"));
            assertEquals(List.of("old"), List.copyOf(destroyed));
        }
    }

    @Test
    void shutdownDestroysHandlerAfterPendingMessagesAndTheirFollowupTasks() throws Exception {
        ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (TestConsumer consumer = new TestConsumer(true, (id, first) -> new WorkHandler(id) {
            @Override
            public void init(ConsumerRecord<String, byte[]> first) {
                events.add("init");
            }

            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record) {
                events.add("message");
                executor.submit(() -> {
                    events.add("followup");
                });
            }

            @Override
            public void destroy() {
                events.add("destroy");
            }
        })) {
            WorkExecutor executor = consumer.workExecutors[0];
            executor.submit(() -> {
                entered.countDown();
                await(release);
            });
            CompletableFuture<Void> closing;
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                consumer.enqueue(record("id", 0));
                closing = CompletableFuture.runAsync(consumer::close);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!executor.isShuttingDown() && executor.pendingTasks() < 2
                        && System.nanoTime() < deadline) {
                    Thread.sleep(1);
                }
                assertTrue(executor.isShuttingDown() || executor.pendingTasks() >= 2);
            } finally {
                release.countDown();
            }
            closing.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("init", "message", "followup", "destroy"), List.copyOf(events));
        }
    }

    private static ConsumerRecord<String, byte[]> record(String id, long offset) {
        return new ConsumerRecord<>("topic", 0, offset, id, new byte[0]);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test task timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    private static class TestConsumer extends DataDrivenKafkaConsumer {
        private final BiFunction<String, ConsumerRecord<String, byte[]>, WorkHandler> factory;

        TestConsumer(boolean autoRelease,
                     BiFunction<String, ConsumerRecord<String, byte[]>, WorkHandler> factory) {
            super("handler-test", 1, 1000, autoRelease, 0, null, 0,
                    ConsumerParam.get_singleConsumer("topic"));
            this.factory = factory;
        }

        void enqueue(ConsumerRecord<String, byte[]> record) {
            submitRecord(record);
        }

        void drain() throws Exception {
            workExecutors[0].submit(() -> {}).get(5, TimeUnit.SECONDS);
        }

        @Override
        public WorkHandler newHandler(String id, ConsumerRecord<String, byte[]> first) {
            return factory.apply(id, first);
        }
    }
}
