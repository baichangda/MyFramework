package cn.bcd.lib.spring.kafka.ext.threaddriven;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static cn.bcd.lib.spring.kafka.ConsumerTestUtil.awaitClosed;

class ThreadDrivenCloseTest {
    @Test
    void allCallersReturnBeforeShutdownCompletes() throws Exception {
        ThreadDrivenKafkaConsumer consumer = newConsumer();
        SingleThreadEventExecutor executor = consumer.workPool[0];
        CountDownLatch callbackReturnedFromClose = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        AtomicInteger shutdownHooks = new AtomicInteger();
        executor.addShutdownHook(shutdownHooks::incrementAndGet);
        try {
            executor.execute(() -> {
                consumer.close();
                consumer.close();
                callbackReturnedFromClose.countDown();
                await(releaseCallback);
            });
            await(callbackReturnedFromClose);

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
            assertFalse(executor.isTerminated());

            releaseCallback.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            awaitClosed(consumer);
            assertTrue(executor.isTerminated());
            assertEquals(1, shutdownHooks.get());
            consumer.close();
            assertEquals(1, shutdownHooks.get());
        } finally {
            releaseCallback.countDown();
            async(consumer::close).get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void callbackCanCloseAgainAfterAnExternalCallerStartedClosing() throws Exception {
        ThreadDrivenKafkaConsumer consumer = newConsumer();
        SingleThreadEventExecutor executor = consumer.workPool[0];
        CountDownLatch handling = new CountDownLatch(1);
        CountDownLatch callClose = new CountDownLatch(1);
        CountDownLatch callbackReturned = new CountDownLatch(1);
        AtomicInteger shutdownHooks = new AtomicInteger();
        executor.addShutdownHook(shutdownHooks::incrementAndGet);
        try {
            executor.execute(() -> {
                handling.countDown();
                await(callClose);
                consumer.close();
                consumer.close();
                callbackReturned.countDown();
            });
            await(handling);
            CompletableFuture<Void> first = async(consumer::close);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!executor.isShuttingDown()) {
                assertTrue(System.nanoTime() < deadline, "Timed out waiting for shutdown to start");
                Thread.sleep(1);
            }
            first.get(5, TimeUnit.SECONDS);
            CompletableFuture<Void> second = async(consumer::close);
            second.get(5, TimeUnit.SECONDS);
            assertFalse(executor.isTerminated());

            callClose.countDown();
            await(callbackReturned);
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            awaitClosed(consumer);
            assertTrue(executor.isTerminated());
            assertEquals(1, shutdownHooks.get());
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

    private static ThreadDrivenKafkaConsumer newConsumer() {
        return new ThreadDrivenKafkaConsumer("thread-close-test", 1, 0, true, 0, 0,
                ConsumerParam.get_singleConsumer("topic")) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record, SingleThreadEventExecutor executor) {
            }
        };
    }
}
