package cn.bcd.lib.base.executor.consume;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumeExecutorRegressionTest {

    @Test
    @Timeout(10)
    void executorShouldContinueAfterRuntimeException() throws Exception {
        assertExecutorContinues(() -> {
            throw new IllegalStateException("expected task failure");
        });
    }

    @Test
    @Timeout(10)
    void executorShouldContinueAfterError() throws Exception {
        assertExecutorContinues(() -> {
            throw new AssertionError("expected task failure");
        });
    }

    private static void assertExecutorContinues(Runnable failingTask) throws Exception {
        ConsumeExecutor<String> executor = new ConsumeExecutor<>("task-failure-test", 0);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.submit(() -> {
                started.countDown();
                release.await();
                return null;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            executor.execute(failingTask);
            var following = executor.submit(() -> 42);
            release.countDown();

            assertEquals(42, following.get(5, TimeUnit.SECONDS));
            assertFalse(executor.isShuttingDown());
            assertEquals(43, executor.submit(() -> 43).get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.close();
        }
    }

    @Test
    @Timeout(10)
    void closeShouldDrainMessagesBeforeDestroyingEntities() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> events = new ArrayList<>();
        ConsumeExecutorGroup<String> group = new ConsumeExecutorGroup<>("drain-test", 1, 2, null, 0) {
            @Override
            public String id(String message) {
                return message;
            }

            @Override
            public ConsumeEntity<String> newEntity(String id, String first) {
                return new ConsumeEntity<>(id) {
                    @Override
                    public void onMessage(String message) throws Exception {
                        started.countDown();
                        release.await();
                        events.add("message:" + message);
                    }

                    @Override
                    public void destroy() {
                        events.add("destroy:" + id);
                    }
                };
            }
        };
        try {
            group.onMessage("a");
            assertTrue(started.await(5, TimeUnit.SECONDS));
            group.onMessage("a");
            group.onMessage("b");

            var closed = group.closeAsync();
            assertThrows(IllegalStateException.class, () -> group.onMessage("c"));
            assertFalse(closed.isDone());
            release.countDown();
            closed.get(5, TimeUnit.SECONDS);

            assertEquals(5, events.size());
            assertEquals(List.of("message:a", "message:a", "message:b"), events.subList(0, 3));
            assertEquals(Set.of("destroy:a", "destroy:b"), new HashSet<>(events.subList(3, 5)));
            assertTrue(group.executors[0].isTerminated());
        } finally {
            release.countDown();
            group.close();
        }
    }
}
