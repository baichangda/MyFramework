package cn.bcd.lib.spring.kafka;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

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
    void roundRobinUsesMultipleExecutorsConcurrently() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Set<SingleThreadEventExecutor> executors = ConcurrentHashMap.newKeySet();
        try (TestConsumer consumer = new TestConsumer((record, executor) -> {
            executors.add(executor);
            entered.countDown();
            await(release);
        })) {
            try {
                consumer.enqueue(record(0));
                consumer.enqueue(record(1));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(2, executors.size());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void roundRobinDistributesSameKeyAndNullKeyRecordsEvenly() {
        Map<SingleThreadEventExecutor, AtomicInteger> counts = new ConcurrentHashMap<>();
        try (TestConsumer consumer = new TestConsumer((record, executor) ->
                counts.computeIfAbsent(executor, ignored -> new AtomicInteger()).incrementAndGet())) {
            for (int i = 0; i < 100; i++) {
                consumer.enqueue(record(i));
            }
            consumer.enqueue(new ConsumerRecord<>("topic", 0, 100, null, new byte[0]));
            consumer.enqueue(new ConsumerRecord<>("topic", 0, 101, null, new byte[0]));
            consumer.close();
            assertEquals(2, counts.size());
            for (SingleThreadEventExecutor executor : consumer.workPool) {
                assertEquals(51, counts.get(executor).get());
            }
            assertEquals(0, consumer.blockingNum.sum());
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
            assertEquals(0, consumer.blockingNum.sum());
        }
    }

    private static ConsumerRecord<String, byte[]> record(long offset) {
        return new ConsumerRecord<>("topic", 0, offset, "same-key", new byte[0]);
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
