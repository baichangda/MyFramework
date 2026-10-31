package cn.bcd.lib.spring.kafka;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static cn.bcd.lib.spring.kafka.ConsumerTestUtil.awaitClosed;

class ThreadDrivenExecutorTest {

    @Test
    void callbacksAndFollowupTasksRunOnTheirAssignedExecutor() throws Exception {
        CountDownLatch completed = new CountDownLatch(260);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        try (TestConsumer consumer = new TestConsumer((record, executor) -> {
            try {
                assertTrue(executor.inEventLoop());
                executor.submit(() -> {
                    try {
                        assertTrue(executor.inEventLoop());
                    } catch (Throwable ex) {
                        errors.add(ex);
                    } finally {
                        completed.countDown();
                    }
                });
            } catch (Throwable ex) {
                errors.add(ex);
                completed.countDown();
            }
        })) {
            for (int i = 0; i < 260; i++) {
                consumer.enqueue(record(i));
            }
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertTrue(errors.isEmpty(), errors.toString());
            assertEquals(0, consumer.blockingNum.sum());
        }
    }

    @Test
    void differentIdsUseDifferentExecutorsConcurrently() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Set<SingleThreadEventExecutor> executors = ConcurrentHashMap.newKeySet();
        try (TestConsumer consumer = new TestConsumer((record, executor) -> {
            executors.add(executor);
            entered.countDown();
            await(release);
        })) {
            try {
                assertNotSame(consumer.getWorkExecutor("a"), consumer.getWorkExecutor("b"));
                consumer.enqueue(record(0, "a"));
                consumer.enqueue(record(1, "b"));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(2, executors.size());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void sameIdMessagesUseOneExecutorAndKeepTheirOrder() {
        Set<SingleThreadEventExecutor> executors = ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<Long> offsets = new ConcurrentLinkedQueue<>();
        try (TestConsumer consumer = new TestConsumer((record, executor) -> {
            executors.add(executor);
            offsets.add(record.offset());
        })) {
            for (int i = 0; i < 100; i++) {
                consumer.enqueue(record(i));
            }
            consumer.close();
            awaitClosed(consumer);
            assertEquals(Set.of(consumer.getWorkExecutor("same-key")), executors);
            assertEquals(LongStream.range(0, 100).boxed().toList(), List.copyOf(offsets));
            assertEquals(0, consumer.blockingNum.sum());
        }
    }

    @Test
    void nullIdUsesFirstExecutor() {
        ConcurrentLinkedQueue<SingleThreadEventExecutor> executors = new ConcurrentLinkedQueue<>();
        try (TestConsumer consumer = new TestConsumer((record, executor) -> executors.add(executor))) {
            assertSame(consumer.workPool[0], consumer.getWorkExecutor(null));
            consumer.enqueue(record(0, null));
            consumer.close();
            awaitClosed(consumer);
            assertEquals(List.of(consumer.workPool[0]), List.copyOf(executors));
            assertEquals(0, consumer.blockingNum.sum());
        }
    }

    @Test
    void customIdAndExecutorSelectionAreUsedForMessageRouting() {
        Map<Long, SingleThreadEventExecutor> executors = new ConcurrentHashMap<>();
        try (TestConsumer consumer = new TestConsumer((record, executor) ->
                executors.put(record.offset(), executor)) {
            @Override
            protected String id(ConsumerRecord<String, byte[]> record) {
                return record.offset() % 2 == 0 ? "even" : "odd";
            }

            @Override
            public SingleThreadEventExecutor getWorkExecutor(String id) {
                return workPool["even".equals(id) ? 1 : 0];
            }
        }) {
            consumer.enqueue(record(0));
            consumer.enqueue(record(1));
            consumer.enqueue(record(2));
            consumer.close();
            awaitClosed(consumer);
            assertSame(consumer.workPool[1], executors.get(0L));
            assertSame(consumer.workPool[0], executors.get(1L));
            assertSame(consumer.workPool[1], executors.get(2L));
        }
    }

    @Test
    void callbackCanCloseConsumerAndPendingMessagesStillDrain() throws Exception {
        CountDownLatch completed = new CountDownLatch(10);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (TestConsumer consumer = new TestConsumer((record, executor) -> completed.countDown()) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record, SingleThreadEventExecutor executor) {
                close();
                super.onMessage(record, executor);
            }
        }) {
            for (SingleThreadEventExecutor executor : consumer.workPool) {
                executor.submit(() -> {
                    entered.countDown();
                    await(release);
                });
            }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                for (int i = 0; i < 10; i++) {
                    consumer.enqueue(record(i));
                }
            } finally {
                release.countDown();
            }
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            for (SingleThreadEventExecutor executor : consumer.workPool) {
                executor.terminationFuture().get(5, TimeUnit.SECONDS);
            }
            assertEquals(0, consumer.blockingNum.sum());
        }
    }

    @Test
    void callbackFailureDoesNotStopFollowingMessagesOnTheSameExecutor() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        try (TestConsumer consumer = new TestConsumer((record, executor) -> {
            if (record.offset() == 0) {
                throw new IllegalStateException("callback failure");
            }
            if (record.offset() == 2) {
                completed.countDown();
            }
        })) {
            consumer.enqueue(record(0));
            consumer.enqueue(record(1));
            consumer.enqueue(record(2));
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            consumer.close();
            awaitClosed(consumer);
            assertEquals(0, consumer.blockingNum.sum());
        }
    }

    private static ConsumerRecord<String, byte[]> record(long offset) {
        return record(offset, "same-key");
    }

    private static ConsumerRecord<String, byte[]> record(long offset, String key) {
        return new ConsumerRecord<>("topic", 0, offset, key, new byte[0]);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test task timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    private static class TestConsumer extends ThreadDrivenKafkaConsumer {
        private final BiConsumer<ConsumerRecord<String, byte[]>, SingleThreadEventExecutor> callback;

        TestConsumer(BiConsumer<ConsumerRecord<String, byte[]>, SingleThreadEventExecutor> callback) {
            super("executor-test", 2, 1000, true, 0, 0, ConsumerParam.get_singleConsumer("topic"));
            this.callback = callback;
        }

        void enqueue(ConsumerRecord<String, byte[]> record) {
            submitRecord(record);
        }

        @Override
        public void onMessage(ConsumerRecord<String, byte[]> record, SingleThreadEventExecutor executor) {
            callback.accept(record, executor);
        }
    }
}
