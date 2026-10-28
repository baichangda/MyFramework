package cn.bcd.app.bp.backend.task;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskWorkerTest {
    @Test
    void lifecycleRunsSeriallyAndCallbackFailureDoesNotChangeSuccess() {
        TaskService service = mock(TaskService.class);
        List<String> calls = new ArrayList<>();
        Thread owner = Thread.currentThread();
        TaskHandler<String> handler = new StringHandler() {
            @Override
            public void onStarted(TaskContext context, String payload) {
                assertSame(owner, Thread.currentThread());
                calls.add("started");
                throw new IllegalStateException("callback error");
            }

            @Override
            public String execute(TaskContext context, String payload) {
                assertSame(owner, Thread.currentThread());
                calls.add("execute");
                return "file.xlsx";
            }

            @Override
            public void onSucceeded(TaskContext context, String payload) {
                assertSame(owner, Thread.currentThread());
                calls.add("succeeded");
            }
        };
        TaskBean task = task(1);
        new TaskWorker(service, new TaskProperties(), List.of(handler)).execute(task);
        assertEquals(List.of("started", "execute", "succeeded"), calls);
        assertEquals(3, task.status);
        assertEquals(100F, task.percent);
        assertEquals("file.xlsx", task.result);
        assertNotNull(task.finishTime);
        verify(service).finish(task);
    }

    @Test
    void stopBeforeRegistrationIsReadFromDatabase() {
        TaskService service = mock(TaskService.class);
        when(service.isStopRequested(1)).thenReturn(true);
        AtomicInteger stopped = new AtomicInteger();
        TaskHandler<String> handler = stoppingHandler(stopped);
        TaskBean task = task(1);
        new TaskWorker(service, new TaskProperties(), List.of(handler)).execute(task);
        assertEquals(6, task.status);
        assertEquals(1, stopped.get());
        verify(service).finish(task);
    }

    @Test
    void broadcastAfterRegistrationIsNotOverwrittenByDatabaseRead() {
        TaskService service = mock(TaskService.class);
        AtomicInteger stopped = new AtomicInteger();
        TaskWorker worker = new TaskWorker(service, new TaskProperties(), List.of(stoppingHandler(stopped)));
        when(service.isStopRequested(1)).thenAnswer(invocation -> {
            worker.requestStop(1);
            return false;
        });
        TaskBean task = task(1);
        worker.execute(task);
        assertEquals(6, task.status);
        assertEquals(1, stopped.get());
    }

    @Test
    void runningTaskExitsAtCheckpointAndUsesTheSameThreadForStopCallback() {
        TaskService service = mock(TaskService.class);
        TaskWorker[] worker = new TaskWorker[1];
        Thread owner = Thread.currentThread();
        TaskHandler<String> handler = new StringHandler() {
            @Override
            public String execute(TaskContext context, String payload) {
                worker[0].requestStop(context.task().id);
                context.checkStop();
                fail("must stop at checkpoint");
                return null;
            }

            @Override
            public void onStopped(TaskContext context, String payload) {
                assertSame(owner, Thread.currentThread());
                context.task().message = "stopped";
            }
        };
        worker[0] = new TaskWorker(service, new TaskProperties(), List.of(handler));
        TaskBean task = task(1);
        worker[0].execute(task);
        assertEquals(6, task.status);
        assertEquals("stopped", task.message);
    }

    @Test
    void businessFailureAndInvalidPayloadAreSavedAsFailed() {
        TaskService service = mock(TaskService.class);
        TaskHandler<String> handler = new StringHandler() {
            @Override
            public String execute(TaskContext context, String payload) {
                throw new IllegalArgumentException("failure");
            }
        };
        TaskWorker worker = new TaskWorker(service, new TaskProperties(), List.of(handler));
        TaskBean task = task(1);
        worker.execute(task);
        assertEquals(4, task.status);
        assertEquals("failure", task.message);
        TaskBean malformed = task(2);
        malformed.payload = "{";
        worker.execute(malformed);
        assertEquals(4, malformed.status);
        verify(service).finish(malformed);
    }

    @Test
    void cancellationUsesCallerThreadAndDoesNotEnterBusinessExecution() {
        TaskService service = mock(TaskService.class);
        Thread owner = Thread.currentThread();
        TaskHandler<String> handler = new StringHandler() {
            @Override
            public void onCanceled(TaskContext context, String payload) {
                assertSame(owner, Thread.currentThread());
                context.task().message = "canceled";
            }
        };
        TaskBean task = task(1);
        task.status = 5;
        new TaskWorker(service, new TaskProperties(), List.of(handler)).canceled(task);
        assertEquals("canceled", task.message);
        verify(service).saveCanceled(task);
        verify(service, never()).finish(any());
    }

    @Test
    void nodeDoesNotPrefetchBeyondItsConcurrency() throws Exception {
        TaskService service = mock(TaskService.class);
        AtomicInteger next = new AtomicInteger();
        when(service.findWaiting(anyCollection())).thenAnswer(invocation -> {
            int id = next.incrementAndGet();
            return id <= 3 ? (long) id : null;
        });
        when(service.claim(anyLong(), anyString())).thenReturn(true);
        when(service.get(anyLong())).thenAnswer(invocation -> task(invocation.getArgument(0)));
        CountDownLatch firstTwo = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch third = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        TaskHandler<String> handler = new StringHandler() {
            @Override
            public String execute(TaskContext context, String payload) throws Exception {
                int count = active.incrementAndGet();
                maxActive.accumulateAndGet(count, Math::max);
                try {
                    if (context.task().id <= 2) {
                        firstTwo.countDown();
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test timed out");
                        }
                    } else {
                        third.countDown();
                    }
                    return null;
                } finally {
                    active.decrementAndGet();
                }
            }
        };
        TaskProperties properties = new TaskProperties();
        properties.setPollIntervals(List.of(Duration.ofMillis(1)));
        TaskWorker worker = new TaskWorker(service, properties, List.of(handler));
        try {
            worker.start();
            assertTrue(firstTwo.await(5, TimeUnit.SECONDS));
            assertEquals(2, next.get(), "busy Workers must not fetch a third task");
            assertFalse(third.await(50, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(third.await(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            worker.stop();
        }
        assertEquals(2, maxActive.get());
    }

    private TaskHandler<String> stoppingHandler(AtomicInteger stopped) {
        return new StringHandler() {
            @Override
            public void onStopped(TaskContext context, String payload) {
                stopped.incrementAndGet();
            }
        };
    }

    private static TaskBean task(long id) {
        TaskBean task = new TaskBean("test", "export");
        task.id = id;
        task.payload = "\"payload\"";
        task.status = 2;
        return task;
    }

    private static class StringHandler implements TaskHandler<String> {
        public String type() { return "export"; }
        public Class<String> payloadType() { return String.class; }
        public String execute(TaskContext context, String payload) throws Exception {
            throw new IllegalStateException("business must not execute in this test");
        }
    }
}
