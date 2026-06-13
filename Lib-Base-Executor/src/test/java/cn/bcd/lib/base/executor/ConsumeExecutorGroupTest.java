package cn.bcd.lib.base.executor;

import cn.bcd.lib.base.executor.consume.ConsumeEntity;
import cn.bcd.lib.base.executor.consume.ConsumeExecutor;
import cn.bcd.lib.base.executor.consume.ConsumeExecutorGroup;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConsumeExecutorGroupTest {

    @Test
    void idExecutorRoundsThreadCountUpToPowerOfTwo() {
        IdEventExecutorGroup group = new IdEventExecutorGroup(3);
        try {
            for (int id = Integer.MIN_VALUE; id < Integer.MIN_VALUE + 100; id++) {
                assertNotNull(group.getEventExecutor(id));
            }
            assertEquals(4, group.executorNum);
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test
    void boundedQueueRejectsExcessTasks() throws Exception {
        ConsumeExecutor<String> executor = new ConsumeExecutor<>("bounded-test", 1);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(running.await(2, TimeUnit.SECONDS));
            executor.execute(() -> {
            });
            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
            }));
        } finally {
            release.countDown();
            executor.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test
    void predicateTrueRemovesEntity() throws Exception {
        TestGroup group = new TestGroup(false);
        try {
            group.onMessage("id");
            assertNotNull(group.getEntity("id").get(2, TimeUnit.SECONDS));
            group.removeEntityIf("id", entity -> true).get(2, TimeUnit.SECONDS);
            assertNull(group.getEntity("id").get(2, TimeUnit.SECONDS));
            assertEquals(1, group.destroyed.get());
        } finally {
            group.close();
        }
    }

    @Test
    void failedInitializationDestroysPartialEntity() throws Exception {
        TestGroup group = new TestGroup(true);
        try {
            group.onMessage("id");
            assertNull(group.getEntity("id").get(2, TimeUnit.SECONDS));
            assertEquals(1, group.destroyed.get());
        } finally {
            group.close();
        }
    }

    @Test
    void closeFromExecutorThreadDoesNotWaitForItself() throws Exception {
        TestGroup group = new TestGroup(false);
        Future<?> closeFuture = group.executors[0].submit(() -> {
            group.close();
            return null;
        });
        assertTrue(closeFuture.await(2, TimeUnit.SECONDS));
        assertTrue(group.executors[0].terminationFuture().await(2, TimeUnit.SECONDS));
    }

    @Test
    void monitorLogWorksWhenMonitoringIsDisabled() throws Exception {
        TestGroup group = new TestGroup(false);
        try {
            assertTrue(group.monitorLog().contains("monitor disabled"));
        } finally {
            group.close();
        }
    }

    private static final class TestGroup extends ConsumeExecutorGroup<String> {
        final boolean failInit;
        final AtomicInteger destroyed = new AtomicInteger();

        TestGroup(boolean failInit) {
            super("test", 1, 0, null, 0);
            this.failInit = failInit;
        }

        @Override
        public String id(String message) {
            return message;
        }

        @Override
        public ConsumeEntity<String> newEntity(String id, String first) {
            return new ConsumeEntity<>(id) {
                final AtomicBoolean initialized = new AtomicBoolean();

                @Override
                public void init(String message) {
                    initialized.set(true);
                    if (failInit) {
                        throw new IllegalStateException("expected");
                    }
                }

                @Override
                public void onMessage(String message) {
                    assertTrue(initialized.get());
                }

                @Override
                public void destroy() {
                    destroyed.incrementAndGet();
                }
            };
        }
    }
}
