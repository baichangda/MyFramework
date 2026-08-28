package cn.bcd.lib.base.executor;

import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdEventExecutorGroupTest {

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
    void closeShouldTerminateExecutorsAndRejectNewTasks() {
        IdEventExecutorGroup group = new IdEventExecutorGroup(1);
        EventExecutor executor = group.getEventExecutor("vehicle-001");

        group.close();

        assertTrue(executor.isTerminated());
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
        }));
    }
}
