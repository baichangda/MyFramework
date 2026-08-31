package cn.bcd.lib.base.executor;

import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdEventExecutorGroupTest {

    private static final AtomicInteger NEW_EXECUTOR_COUNT = new AtomicInteger();

    @Test
    void shouldAllocateSameExecutorForSameId() {
        try (IdEventExecutorGroup group = new IdEventExecutorGroup(4)) {
            assertSame(group.getEventExecutor("vehicle-001"), group.getEventExecutor("vehicle-001"));
        }
    }

    @Test
    void shouldAllocateDifferentExecutorsByIdHash() {
        try (IdEventExecutorGroup group = new IdEventExecutorGroup(4)) {
            assertNotSame(group.getEventExecutor(0), group.getEventExecutor(1));
        }
    }

    @Test
    void subclassShouldCreateExecutors() {
        NEW_EXECUTOR_COUNT.set(0);

        try (IdEventExecutorGroup ignored = new CustomIdEventExecutorGroup(3)) {
            assertEquals(4, NEW_EXECUTOR_COUNT.get());
        }
    }

    @Test
    void closeShouldTerminateExecutorsAndRejectNewTasks() {
        IdEventExecutorGroup group = new IdEventExecutorGroup(1);
        EventExecutor executor = group.getEventExecutor("vehicle-001");

        group.close();

        assertTrue(executor.isTerminated());
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
        }));
    }

    @Test
    void executeAndSubmitShouldRunCommands() throws Exception {
        CountDownLatch executeLatch = new CountDownLatch(1);
        AtomicReference<String> executeResult = new AtomicReference<>();
        AtomicReference<String> submitResult = new AtomicReference<>();

        try (IdEventExecutorGroup group = new IdEventExecutorGroup(1)) {
            group.execute("execute-id", () -> {
                executeResult.set("executed");
                executeLatch.countDown();
            });
            Future<?> future = group.submit("submit-id", () -> submitResult.set("submitted"));

            assertTrue(executeLatch.await(2, TimeUnit.SECONDS));
            future.get(2, TimeUnit.SECONDS);
            assertEquals("executed", executeResult.get());
            assertEquals("submitted", submitResult.get());
        }
    }

    @Test
    void scheduleShouldRunCommand() throws Exception {
        AtomicReference<String> result = new AtomicReference<>();

        try (IdEventExecutorGroup group = new IdEventExecutorGroup(1)) {
            ScheduledFuture<?> future = group.schedule(
                    "scheduled-id", () -> result.set("scheduled"), 10, TimeUnit.MILLISECONDS);

            future.get(2, TimeUnit.SECONDS);
            assertEquals("scheduled", result.get());
        }
    }

    @Test
    void callableOverloadsShouldReturnResults() throws Exception {
        try (IdEventExecutorGroup group = new IdEventExecutorGroup(1)) {
            Future<String> submitted = group.submit("submit-id", () -> "submitted");
            ScheduledFuture<String> scheduled = group.schedule(
                    "schedule-id", () -> "scheduled", 10, TimeUnit.MILLISECONDS);

            assertEquals("submitted", submitted.get(2, TimeUnit.SECONDS));
            assertEquals("scheduled", scheduled.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void periodicSchedulesShouldRunCommands() throws Exception {
        CountDownLatch fixedRateLatch = new CountDownLatch(2);
        CountDownLatch fixedDelayLatch = new CountDownLatch(2);

        try (IdEventExecutorGroup group = new IdEventExecutorGroup(2)) {
            ScheduledFuture<?> fixedRate = group.scheduleAtFixedRate(
                    "fixed-rate-id",
                    fixedRateLatch::countDown,
                    0, 10, TimeUnit.MILLISECONDS);
            ScheduledFuture<?> fixedDelay = group.scheduleWithFixedDelay(
                    "fixed-delay-id",
                    fixedDelayLatch::countDown,
                    0, 10, TimeUnit.MILLISECONDS);

            assertTrue(fixedRateLatch.await(2, TimeUnit.SECONDS));
            assertTrue(fixedDelayLatch.await(2, TimeUnit.SECONDS));
            fixedRate.cancel(false);
            fixedDelay.cancel(false);
        }
    }

    @Test
    void taskMethodsShouldRejectNullArguments() {
        try (IdEventExecutorGroup group = new IdEventExecutorGroup(1)) {
            assertThrows(NullPointerException.class, () -> group.execute(null, () -> {
            }));
            assertThrows(NullPointerException.class, () -> group.submit("id", (Runnable) null));
            assertThrows(NullPointerException.class, () -> group.submit("id", (Callable<?>) null));
            assertThrows(NullPointerException.class,
                    () -> group.schedule("id", () -> {
                    }, 0, null));
            assertThrows(NullPointerException.class,
                    () -> group.schedule("id", (Callable<?>) null, 0, TimeUnit.SECONDS));
        }
    }

    private static final class CustomIdEventExecutorGroup extends IdEventExecutorGroup {

        private CustomIdEventExecutorGroup(int nThreads) {
            super(nThreads);
        }

        @Override
        protected EventExecutor newEventExecutor(int index) {
            NEW_EXECUTOR_COUNT.incrementAndGet();
            return super.newEventExecutor(index);
        }
    }
}
