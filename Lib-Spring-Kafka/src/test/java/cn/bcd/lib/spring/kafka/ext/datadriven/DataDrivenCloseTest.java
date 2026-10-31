package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static cn.bcd.lib.spring.kafka.ConsumerTestUtil.awaitClosed;

class DataDrivenCloseTest {
    @Test
    void allCallersReturnBeforeShutdownCompletes() throws Exception {
        CountDownLatch destroying = new CountDownLatch(1);
        CountDownLatch releaseDestroy = new CountDownLatch(1);
        AtomicInteger destroys = new AtomicInteger();
        TestConsumer consumer = new TestConsumer(handler -> {}, handler -> {
            // Even during shutdown, owned threads must never wait for themselves.
            handler.consumer.close();
            destroying.countDown();
            await(releaseDestroy);
            destroys.incrementAndGet();
        });
        try {
            consumer.enqueue();
            consumer.workExecutors[0].submit(() -> {
                consumer.close();
                consumer.close();
            }).get(5, TimeUnit.SECONDS);
            await(destroying);

            CountDownLatch callersEntered = new CountDownLatch(2);
            Runnable close = () -> {
                callersEntered.countDown();
                consumer.close();
            };
            CompletableFuture<Void> first = async(close);
            CompletableFuture<Void> second = async(close);
            await(callersEntered);
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertFalse(consumer.workExecutors[0].isTerminated());

            releaseDestroy.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            awaitClosed(consumer);
            assertTrue(consumer.workExecutors[0].isTerminated());
            assertEquals(1, destroys.get());
            consumer.close();
            assertEquals(1, destroys.get());
        } finally {
            releaseDestroy.countDown();
            async(consumer::close).get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void callbackCanCloseAgainAfterAnExternalCallerStartedClosing() throws Exception {
        CountDownLatch handling = new CountDownLatch(1);
        CountDownLatch callClose = new CountDownLatch(1);
        CountDownLatch callbackReturned = new CountDownLatch(1);
        AtomicInteger destroys = new AtomicInteger();
        TestConsumer consumer = new TestConsumer(handler -> {
            handling.countDown();
            await(callClose);
            handler.consumer.close();
            handler.consumer.close();
            callbackReturned.countDown();
        }, handler -> destroys.incrementAndGet());
        try {
            consumer.enqueue();
            await(handling);
            CompletableFuture<Void> closing = async(consumer::close);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!consumer.workExecutors[0].isShuttingDown()) {
                assertTrue(System.nanoTime() < deadline, "Timed out waiting for shutdown to start");
                Thread.sleep(1);
            }
            closing.get(5, TimeUnit.SECONDS);
            CompletableFuture<Void> second = async(consumer::close);
            second.get(5, TimeUnit.SECONDS);
            assertFalse(consumer.workExecutors[0].isTerminated());

            callClose.countDown();
            await(callbackReturned);
            closing.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            awaitClosed(consumer);
            assertTrue(consumer.workExecutors[0].isTerminated());
            assertEquals(1, destroys.get());
        } finally {
            callClose.countDown();
            async(consumer::close).get(5, TimeUnit.SECONDS);
        }
    }

    private static CompletableFuture<Void> async(Runnable action) {
        return CompletableFuture.runAsync(action, Thread::startVirtualThread);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for test coordination");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class TestConsumer extends DataDrivenKafkaConsumer {
        private final Consumer<WorkHandler> onMessage;
        private final Consumer<WorkHandler> onDestroy;

        TestConsumer(Consumer<WorkHandler> onMessage, Consumer<WorkHandler> onDestroy) {
            super("close-test", 1, 0, true, 0, null, 0,
                    ConsumerParam.get_singleConsumer("topic"));
            this.onMessage = onMessage;
            this.onDestroy = onDestroy;
        }

        void enqueue() {
            submitRecord(new ConsumerRecord<>("topic", 0, 0, "id", new byte[0]));
        }

        @Override
        public WorkHandler newHandler(String id, ConsumerRecord<String, byte[]> first) {
            return new WorkHandler(id) {
                @Override
                public void onMessage(ConsumerRecord<String, byte[]> record) {
                    onMessage.accept(this);
                }

                @Override
                public void destroy() {
                    onDestroy.accept(this);
                }
            };
        }
    }
}
