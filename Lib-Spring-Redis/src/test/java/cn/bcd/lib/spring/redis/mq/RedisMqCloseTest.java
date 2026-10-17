package cn.bcd.lib.spring.redis.mq;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class RedisMqCloseTest {
    @Test
    void queueCloseDrainsRunningQueuedAndInProgressPopMessages() throws Exception {
        RedisQueueMQ<String> queue = new RedisQueueMQ<>("queue", connectionFactory(), String.class, 1, 1);
        verifyGracefulClose(queue, "consumeExecutor", "workExecutor", queue::close);
    }

    @Test
    void topicCloseStopsSubscriptionsBeforeDrainingCallbacks() throws Exception {
        RedisTopicMQ<String> topic = new RedisTopicMQ<>(connectionFactory(), 1, 1, String.class, "topic");
        AtomicBoolean destroyed = new AtomicBoolean();
        AtomicBoolean listenerRemoved = new AtomicBoolean();
        RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
            @Override
            public void removeMessageListener(MessageListener listener) {
                listenerRemoved.set(true);
            }

            @Override
            public void destroy() {
                destroyed.set(true);
            }
        };
        setField(topic, "redisMessageListenerContainer", container);
        verifyGracefulClose(topic, "subscriptionExecutor", "taskExecutor", topic::close);
        assertTrue(destroyed.get());
        assertFalse(listenerRemoved.get(), "Keep listeners available until subscriptions have stopped");
    }

    private static void verifyGracefulClose(Object mq, String receiverField, String workerField,
                                             Runnable close) throws Exception {
        ThreadPoolExecutor receivers = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        ThreadPoolExecutor workers = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch finishHandler = new CountDownLatch(1);
        CountDownLatch receiving = new CountDownLatch(1);
        CountDownLatch finishReceive = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean interrupted = new AtomicBoolean();
        setField(mq, receiverField, receivers);
        setField(mq, workerField, workers);
        setField(mq, "consumerAvailable", true);
        try {
            workers.execute(() -> {
                handlerStarted.countDown();
                await(finishHandler, interrupted);
                completed.incrementAndGet();
            });
            workers.execute(completed::incrementAndGet);
            receivers.execute(() -> {
                receiving.countDown();
                await(finishReceive, interrupted);
                workers.execute(completed::incrementAndGet);
            });
            assertTrue(handlerStarted.await(5, TimeUnit.SECONDS));
            assertTrue(receiving.await(5, TimeUnit.SECONDS));

            CompletableFuture<Void> closing = CompletableFuture.runAsync(close);
            awaitCondition(receivers::isShutdown);
            assertFalse(workers.isShutdown(), "The in-progress receive must still be able to submit its message");
            finishReceive.countDown();
            awaitCondition(workers::isShutdown);
            assertFalse(closing.isDone(), "Close must wait for the running handler and queued messages");
            finishHandler.countDown();
            closing.get(5, TimeUnit.SECONDS);

            assertEquals(3, completed.get());
            assertFalse(interrupted.get());
            assertTrue(receivers.isTerminated());
            assertTrue(workers.isTerminated());
        } finally {
            finishReceive.countDown();
            finishHandler.countDown();
            receivers.shutdownNow();
            workers.shutdownNow();
            assertTrue(receivers.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch, AtomicBoolean interrupted) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            interrupted.set(true);
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "Timed out waiting for executor shutdown");
            Thread.sleep(1);
        }
    }

    private static void setField(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static RedisConnectionFactory connectionFactory() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(), new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> {
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }
}
