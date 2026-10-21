package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ExecutorUtilTest {
    @Test
    void cancelledFuturesDoNotPreventRemainingResourcesFromClosing() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            for (boolean all : new boolean[]{false, true}) {
                for (boolean interrupt : new boolean[]{false, true}) {
                    FutureTask<Void> single = new FutureTask<>(() -> null);
                    FutureTask<Void> first = new FutureTask<>(() -> null);
                    FutureTask<Void> second = new FutureTask<>(() -> null);
                    ExecutorService pool = Executors.newSingleThreadExecutor();
                    try {
                        Object[] resources = {single, new Future<?>[]{first, second}, pool};
                        if (all) {
                            ExecutorUtil.shutdownAllThenAwait(interrupt, resources);
                        } else {
                            ExecutorUtil.shutdownThenAwait(interrupt, resources);
                        }
                        assertTrue(single.isCancelled());
                        assertTrue(first.isCancelled());
                        assertTrue(second.isCancelled());
                        assertTrue(pool.isTerminated());
                    } finally {
                        pool.shutdownNow();
                    }
                }
            }
        });
    }

    @Test
    void awaitContinuesPastAlreadyCancelledFutures() {
        FutureTask<Void> cancelled = new FutureTask<>(() -> null);
        cancelled.cancel(false);
        CompletableFuture<Void> completed = CompletableFuture.completedFuture(null);
        assertDoesNotThrow(() -> ExecutorUtil.await(cancelled, new Future<?>[]{cancelled, completed}));
    }

    @Test
    void shutdownAllStopsResourcesBeforeWaitingForLatchAndQueues() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            CountDownLatch latch = new CountDownLatch(1);
            Queue<Integer> queue = new ConcurrentLinkedQueue<>();
            BlockingQueue<Integer> blockingQueue = new LinkedBlockingQueue<>();
            queue.add(1);
            blockingQueue.add(2);
            FutureTask<Void> release = new FutureTask<>(() -> null) {
                @Override
                protected void done() {
                    queue.clear();
                    blockingQueue.clear();
                    latch.countDown();
                }
            };
            ExecutorUtil.shutdownAllThenAwait(false, latch, queue,
                    new Queue<?>[]{queue}, new BlockingQueue<?>[]{blockingQueue}, release);
            assertTrue(release.isCancelled());
            assertEquals(0, latch.getCount());
            assertTrue(queue.isEmpty());
            assertTrue(blockingQueue.isEmpty());
        });
    }
}
