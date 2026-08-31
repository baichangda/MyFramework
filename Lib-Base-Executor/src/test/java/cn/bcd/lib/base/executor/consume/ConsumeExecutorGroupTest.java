package cn.bcd.lib.base.executor.consume;

import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void interruptedCloseShouldStillTerminateExecutors() throws Exception {
        CountDownLatch messageStarted = new CountDownLatch(1);
        CountDownLatch releaseMessage = new CountDownLatch(1);
        CountDownLatch destroyed = new CountDownLatch(1);
        TestGroup group = new TestGroup(1, 0,
                message -> {
                    messageStarted.countDown();
                    releaseMessage.await();
                },
                destroyed);
        group.onMessage("a");
        assertTrue(messageStarted.await(5, TimeUnit.SECONDS));

        AtomicReference<Throwable> closeError = new AtomicReference<>();
        Thread closeThread = new Thread(() -> {
            try {
                group.close();
            } catch (Throwable ex) {
                closeError.set(ex);
            }
        });
        closeThread.start();
        awaitWaiting(closeThread);
        closeThread.interrupt();
        releaseMessage.countDown();
        closeThread.join(5000);

        assertFalse(closeThread.isAlive());
        assertTrue(closeThread.isInterrupted());
        assertNull(closeError.get());
        assertTrue(destroyed.await(5, TimeUnit.SECONDS));
        assertTrue(group.executors[0].isTerminated());
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.sleep(1);
        }
        throw new AssertionError("close thread did not enter a waiting state");
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
            super("test-consume", executorNum, executorQueueSize, null, 0);
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
