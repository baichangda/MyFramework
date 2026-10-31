package cn.bcd.lib.base.executor.consume;

import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumeExecutorGroupTest {

    @Test
    @Timeout(10)
    void closeFromDifferentExecutorThreadsShouldNotDeadlock() throws Exception {
        CountDownLatch messagesStarted = new CountDownLatch(2);
        CountDownLatch startClosing = new CountDownLatch(1);
        CountDownLatch destroyed = new CountDownLatch(2);
        TestGroup group = new TestGroup(2, 0,
                message -> {
                    messagesStarted.countDown();
                    startClosing.await();
                },
                destroyed);
        group.closeFromMessage = true;

        group.onMessage("a");
        group.onMessage("b");
        assertTrue(messagesStarted.await(5, TimeUnit.SECONDS));
        startClosing.countDown();

        assertTrue(destroyed.await(5, TimeUnit.SECONDS));
        for (ConsumeExecutor<String> executor : group.executors) {
            assertTrue(executor.terminationFuture().await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @Timeout(10)
    void scanShouldRunDirectlyWhenExecutorQueueIsFull() throws Exception {
        CountDownLatch destroyed = new CountDownLatch(1);
        TestGroup group = new TestGroup(1, 2, message -> {
        }, destroyed);
        try {
            group.onMessage("a");
            assertTrue(group.getEntity("a").await(5, TimeUnit.SECONDS));

            ConsumeExecutor<String> executor = group.executors[0];
            CountDownLatch scanTaskStarted = new CountDownLatch(1);
            CountDownLatch queueFilled = new CountDownLatch(1);
            Future<?> scan = executor.submit(() -> {
                scanTaskStarted.countDown();
                queueFilled.await();
                group.scanAndDestroyEntity(executor, Long.MAX_VALUE);
                return null;
            });
            assertTrue(scanTaskStarted.await(5, TimeUnit.SECONDS));
            while (true) {
                try {
                    executor.execute(() -> {
                    });
                } catch (RejectedExecutionException expected) {
                    break;
                }
            }
            queueFilled.countDown();

            assertTrue(scan.await(5, TimeUnit.SECONDS));
            assertTrue(scan.isSuccess());
            assertTrue(destroyed.await(5, TimeUnit.SECONDS));
        } finally {
            group.close();
        }
    }

    @Test
    @Timeout(10)
    void externalCloseShouldReturnBeforeMessagesAndCleanupComplete() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch destroyed = new CountDownLatch(1);
        TestGroup group = new TestGroup(1, 0, message -> {
            started.countDown();
            release.await();
        }, destroyed);
        try {
            group.onMessage("a");
            assertTrue(started.await(5, TimeUnit.SECONDS));
            CompletableFuture.runAsync(() -> {
                group.close();
                group.close();
            }).get(5, TimeUnit.SECONDS);
            var completion = group.closeAsync();
            assertSame(completion, group.closeAsync());
            assertFalse(completion.isDone());
            assertFalse(group.executors[0].isTerminated());
            assertThrows(IllegalStateException.class, () -> group.onMessage("b"));
        } finally {
            release.countDown();
            group.closeAsync().get(5, TimeUnit.SECONDS);
        }
        assertTrue(destroyed.await(5, TimeUnit.SECONDS));
        assertTrue(group.executors[0].isTerminated());
    }

    @Test
    @Timeout(10)
    void monitorThreadCanCloseWithoutWaitingForItself() throws Exception {
        TestGroup group = new TestGroup(1, 0, message -> {}, new CountDownLatch(0), 60);
        try {
            group.monitorPool.submit(group::close).get(5, TimeUnit.SECONDS);
            group.closeAsync().get(5, TimeUnit.SECONDS);
            assertTrue(group.monitorPool.isTerminated());
            assertTrue(group.executors[0].isTerminated());
        } finally {
            group.closeAsync().get(5, TimeUnit.SECONDS);
        }
    }

    @FunctionalInterface
    private interface MessageAction {
        void run(String message) throws Exception;
    }

    private static final class TestGroup extends ConsumeExecutorGroup<String> {
        private final MessageAction messageAction;
        private final CountDownLatch destroyed;
        private volatile boolean closeFromMessage;

        private TestGroup(int executorNum,
                          int executorQueueSize,
                          MessageAction messageAction,
                          CountDownLatch destroyed) {
            this(executorNum, executorQueueSize, messageAction, destroyed, 0);
        }

        private TestGroup(int executorNum,
                          int executorQueueSize,
                          MessageAction messageAction,
                          CountDownLatch destroyed,
                          int monitorPeriod) {
            super("test-consume", executorNum, executorQueueSize, null, monitorPeriod);
            this.messageAction = messageAction;
            this.destroyed = destroyed;
        }

        @Override
        public String id(String message) {
            return message;
        }

        @Override
        public ConsumeEntity<String> newEntity(String id, String first) {
            return new ConsumeEntity<>(id) {
                @Override
                public void onMessage(String message) throws Exception {
                    messageAction.run(message);
                    if (closeFromMessage) {
                        TestGroup.this.close();
                    }
                }

                @Override
                public void destroy() {
                    destroyed.countDown();
                }
            };
        }
    }
}
