package cn.bcd.lib.base.executor;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void concurrentInternalCloseShouldNotWaitForOtherExecutors() throws Exception {
        IdEventExecutorGroup group = new DaemonIdEventExecutorGroup();
        EventExecutor first = group.getEventExecutor(0);
        EventExecutor second = group.getEventExecutor(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Runnable closeTask = () -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("close tasks were not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            group.close();
        };
        try {
            var firstClose = first.submit(closeTask);
            var secondClose = second.submit(closeTask);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            firstClose.get(5, TimeUnit.SECONDS);
            secondClose.get(5, TimeUnit.SECONDS);
            assertTrue(first.terminationFuture().await(5, TimeUnit.SECONDS));
            assertTrue(second.terminationFuture().await(5, TimeUnit.SECONDS));
            group.close();
        } finally {
            start.countDown();
            // Do not wait here: a regression must fail rather than hang the test worker.
            first.shutdownGracefully(0, 0, TimeUnit.SECONDS);
            second.shutdownGracefully(0, 0, TimeUnit.SECONDS);
        }
    }

    private static final class DaemonIdEventExecutorGroup extends IdEventExecutorGroup {
        private DaemonIdEventExecutorGroup() {
            super(2);
        }

        @Override
        protected EventExecutor newEventExecutor(int index) {
            return new DefaultEventExecutor(r -> {
                Thread thread = new Thread(r, "id-executor-close-test-" + index);
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    @Test
    void constructorFailureShouldShutdownCreatedExecutor() throws Exception {
        RuntimeException failure = new IllegalStateException("factory failure");
        assertConstructionFailureCleansUp(failure, IllegalStateException.class);
    }

    @Test
    void nullFactoryResultShouldShutdownCreatedExecutor() throws Exception {
        assertConstructionFailureCleansUp(null, NullPointerException.class);
    }

    @Test
    void constructorErrorShouldShutdownCreatedExecutor() throws Exception {
        assertConstructionFailureCleansUp(new AssertionError("factory error"), AssertionError.class);
    }

    private static <T extends Throwable> void assertConstructionFailureCleansUp(
            Throwable failure, Class<T> expectedType) throws Exception {
        EventExecutor executor = new DefaultEventExecutor();
        FailingIdEventExecutorGroup.first = executor;
        FailingIdEventExecutorGroup.failure = failure;
        try {
            // Start a real worker so the test also checks thread cleanup.
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            T thrown = assertThrows(expectedType, FailingIdEventExecutorGroup::new);
            if (failure != null) {
                assertSame(failure, thrown);
            }
            assertTrue(executor.isShuttingDown());
            assertTrue(executor.terminationFuture().await(5, TimeUnit.SECONDS));
            assertTrue(executor.isTerminated());
        } finally {
            executor.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
            FailingIdEventExecutorGroup.first = null;
            FailingIdEventExecutorGroup.failure = null;
        }
    }

    private static final class FailingIdEventExecutorGroup extends IdEventExecutorGroup {
        private static EventExecutor first;
        private static Throwable failure;

        private FailingIdEventExecutorGroup() {
            super(2);
        }

        @Override
        protected EventExecutor newEventExecutor(int index) {
            if (index == 0) {
                return first;
            }
            if (failure instanceof RuntimeException ex) {
                throw ex;
            }
            if (failure instanceof Error ex) {
                throw ex;
            }
            return null;
        }
    }
}
