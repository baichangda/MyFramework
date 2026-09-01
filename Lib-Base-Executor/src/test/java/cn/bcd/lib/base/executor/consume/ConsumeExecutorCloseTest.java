package cn.bcd.lib.base.executor.consume;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumeExecutorCloseTest {

    @Test
    @Timeout(10)
    void executorCleanupShouldContinueAfterEntityDestroyFailure() throws Exception {
        ConsumeExecutor<String> executor = new ConsumeExecutor<>("cleanup-test", 0);
        CountDownLatch normalDestroyed = new CountDownLatch(1);

        executor.submit(() -> {
            executor.entityMap.put("\0", new ConsumeEntity<>("\0") {
                @Override
                public void onMessage(String message) {
                }

                @Override
                public void destroy() {
                    throw new IllegalStateException("expected");
                }
            });
            executor.entityMap.put("a", new ConsumeEntity<>("a") {
                @Override
                public void onMessage(String message) {
                }

                @Override
                public void destroy() {
                    normalDestroyed.countDown();
                }
            });
        }).sync();

        executor.close();

        assertTrue(normalDestroyed.await(1, TimeUnit.SECONDS));
        assertTrue(executor.entityMap.isEmpty());
        assertTrue(executor.isTerminated());
    }

    @Test
    @Timeout(10)
    void repeatedExecutorCloseShouldNotDestroyEntityTwice() {
        ConsumeExecutor<String> executor = new ConsumeExecutor<>("repeated-close-test", 0);
        AtomicInteger destroyCount = new AtomicInteger();
        executor.submit(() -> executor.entityMap.put("a", new ConsumeEntity<>("a") {
            @Override
            public void onMessage(String message) {
            }

            @Override
            public void destroy() {
                destroyCount.incrementAndGet();
            }
        })).syncUninterruptibly();

        executor.close();
        executor.close();

        assertEquals(1, destroyCount.get());
    }

    @Test
    @Timeout(10)
    void groupCloseAsyncShouldBeIdempotentWhenQueueIsFull() throws Exception {
        CountDownLatch messageStarted = new CountDownLatch(1);
        CountDownLatch releaseMessage = new CountDownLatch(1);
        CountDownLatch destroyed = new CountDownLatch(1);
        ConsumeExecutorGroup<String> group = new ConsumeExecutorGroup<>("full-queue-close-test", 1, 1, null, 0) {
            @Override
            public String id(String message) {
                return message;
            }

            @Override
            public ConsumeEntity<String> newEntity(String id, String first) {
                return new ConsumeEntity<>(id) {
                    @Override
                    public void onMessage(String message) throws Exception {
                        messageStarted.countDown();
                        releaseMessage.await();
                    }

                    @Override
                    public void destroy() {
                        destroyed.countDown();
                    }
                };
            }
        };

        group.onMessage("a");
        assertTrue(messageStarted.await(5, TimeUnit.SECONDS));
        group.onMessage("b");

        CompletableFuture<Void> firstClose = group.closeAsync();
        CompletableFuture<Void> secondClose = group.closeAsync();
        assertSame(firstClose, secondClose);
        releaseMessage.countDown();

        firstClose.get(5, TimeUnit.SECONDS);
        assertTrue(destroyed.await(1, TimeUnit.SECONDS));
        assertTrue(group.executors[0].isTerminated());
    }
}
