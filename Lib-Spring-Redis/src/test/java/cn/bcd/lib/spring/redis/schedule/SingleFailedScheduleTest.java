package cn.bcd.lib.spring.redis.schedule;

import cn.bcd.lib.base.util.DateUtil;

import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.types.Expiration;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SingleFailedScheduleTest {
    @Test
    void renewsBeyondInitialLeaseAndRejectsAnotherInstance() throws Exception {
        RedisStub redis = new RedisStub(4);
        var first = redis.handler(Duration.ofMillis(300));
        var second = redis.handler(Duration.ofMillis(300));
        String taskId = first.doBeforeStart();
        assertNotNull(taskId);
        Thread renewal = renewalThread(first, taskId);
        try {
            assertTrue(redis.renewed.await(5, TimeUnit.SECONDS));
            assertTrue(redis.renewCalls.get() >= 4);
            String rejected = second.doBeforeStart();
            assertNull(rejected, "A long task must still exclude other instances");
            second.doAfterFinish(rejected);
            assertTrue(renewal.isAlive(), "Finishing an unsuccessful start must not stop another invocation");
        } finally {
            first.doAfterFinish(taskId);
            join(renewal);
        }
        assertTrue(leases(first).isEmpty());
        assertEquals(0, redis.deleteCalls.get());
    }

    @Test
    void taskIdsKeepInvocationsIndependentAndAllowFinishingOnAnotherThread() throws Exception {
        RedisStub redis = new RedisStub(0);
        var handler = redis.handler(Duration.ofSeconds(30));
        String firstId = handler.doBeforeStart();
        assertNotNull(firstId);
        Thread firstRenewal = renewalThread(handler, firstId);
        assertEquals("task," + redis.value, firstId);
        String secondId = null;
        Thread secondRenewal = null;
        try {
            String firstToken = redis.value;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (firstToken.equals(DateUtil.FORMATTER_yyyyMMddHHmmss.format(Instant.now()))) {
                assertTrue(System.nanoTime() < deadline, "Timed out waiting for the next second");
                Thread.sleep(1);
            }
            redis.expire();
            secondId = handler.doBeforeStart();
            assertNotNull(secondId);
            assertNotEquals(firstId, secondId);
            secondRenewal = renewalThread(handler, secondId);
            assertNotSame(firstRenewal, secondRenewal);
            assertEquals(2, leases(handler).size());

            CompletableFuture.runAsync(() -> handler.doAfterFinish(firstId)).get(5, TimeUnit.SECONDS);
            join(firstRenewal);
            handler.doAfterFinish(firstId);
            handler.doAfterFinish("unknown-task");
            assertEquals(1, leases(handler).size());
            assertTrue(secondRenewal.isAlive(), "Finishing the old task must not stop the new task");
        } finally {
            handler.doAfterFinish(firstId);
            handler.doAfterFinish(secondId);
            join(firstRenewal);
            if (secondRenewal != null) {
                join(secondRenewal);
            }
        }
        assertTrue(leases(handler).isEmpty());
        assertEquals(0, redis.deleteCalls.get());
    }

    @Test
    void failedAcquisitionReturnsNullAndPreservesAnExistingInvocation() throws Exception {
        RedisStub redis = new RedisStub(0);
        var handler = redis.handler(Duration.ofSeconds(30));
        String taskId = handler.doBeforeStart();
        assertNotNull(taskId);
        Thread renewal = renewalThread(handler, taskId);
        try {
            String rejected = handler.doBeforeStart();
            assertNull(rejected);
            handler.doAfterFinish(rejected);
            assertEquals(2, redis.setCalls.get());
            assertEquals(1, leases(handler).size());
            assertSame(renewal, renewalThread(handler, taskId));
        } finally {
            handler.doAfterFinish(taskId);
            join(renewal);
        }
        assertTrue(leases(handler).isEmpty());
    }

    @Test
    void unsuccessfulRedisResponsesReturnNullWithoutCreatingAnInvocation() {
        RedisStub redis = new RedisStub(0);
        var handler = redis.handler(Duration.ofSeconds(30));
        redis.failAcquisition = true;
        assertNull(handler.doBeforeStart());
        assertDoesNotThrow(() -> handler.doAfterFinish(null));
        redis.failAcquisition = false;
        redis.nullAcquisition = true;
        assertNull(handler.doBeforeStart());
        assertTrue(leases(handler).isEmpty());
        assertEquals(0, redis.renewCalls.get());
    }

    @Test
    void losingOwnershipStopsRenewalWithoutChangingAnotherOwnersExpiry() throws Exception {
        RedisStub redis = new RedisStub(0);
        var handler = redis.handler(Duration.ofMillis(300));
        String taskId = handler.doBeforeStart();
        assertNotNull(taskId);
        Thread renewal = renewalThread(handler, taskId);
        try {
            redis.replace("another-owner", Duration.ofSeconds(10));
            long replacementExpiry = redis.expiresAt;
            join(renewal);
            assertEquals("another-owner", redis.value);
            assertEquals(replacementExpiry, redis.expiresAt);
            assertEquals(1, redis.rejectedRenewals.get());
        } finally {
            handler.doAfterFinish(taskId);
            join(renewal);
        }
        assertTrue(leases(handler).isEmpty());
    }

    @Test
    void redisFailureStopsRenewal() throws Exception {
        RedisStub redis = new RedisStub(0);
        redis.failRenewal = true;
        var handler = redis.handler(Duration.ofMillis(300));
        String taskId = handler.doBeforeStart();
        assertNotNull(taskId);
        try {
            join(renewalThread(handler, taskId));
            assertEquals(1, redis.renewCalls.get());
        } finally {
            handler.doAfterFinish(taskId);
        }
        assertTrue(leases(handler).isEmpty());
    }

    @Test
    void finishIsIdempotentAndLeavesKeyUntilItsExistingExpiry() throws Exception {
        RedisStub redis = new RedisStub(0);
        var handler = redis.handler(Duration.ofSeconds(30));
        handler.doAfterFinish(null);
        handler.doAfterFinish("unknown-task");
        String taskId = handler.doBeforeStart();
        assertNotNull(taskId);
        Thread renewal = renewalThread(handler, taskId);
        String token = redis.value;
        assertTrue(token.matches("[0-9]{14}"));
        long expiry = redis.expiresAt;
        handler.doAfterFinish(taskId);
        handler.doAfterFinish(taskId);
        join(renewal);
        assertTrue(leases(handler).isEmpty());
        assertEquals(token, redis.value);
        assertEquals(expiry, redis.expiresAt);
        assertEquals(0, redis.renewCalls.get());
        assertEquals(0, redis.deleteCalls.get());
    }

    @Test
    void aopStopsRenewalOnSuccessAndFailure() throws Exception {
        for (boolean fail : new boolean[]{false, true}) {
            RedisStub redis = new RedisStub(1);
            Task task = proxy(redis, () -> {
                assertTrue(redis.renewed.await(5, TimeUnit.SECONDS));
                if (fail) {
                    throw new IllegalStateException("task failure");
                }
                return "done";
            });
            assertEquals(fail ? null : "done", task.run());
            assertNotNull(redis.lastRenewalThread);
            join(redis.lastRenewalThread);
            assertEquals(0, redis.deleteCalls.get());
            assertNull(task.run(), "A late trigger must skip the retained key");
            redis.expire();
            assertEquals(fail ? null : "done", task.run());
        }
    }

    @Test
    void aopInstancesUseTheirOwnConnectionFactories() throws Exception {
        RedisStub first = new RedisStub(0);
        RedisStub second = new RedisStub(0);
        Task firstTask = proxy(first, () -> "first");
        Task secondTask = proxy(second, () -> "second");
        assertEquals("first", firstTask.run());
        assertEquals("second", secondTask.run());
        assertEquals(1, first.setCalls.get());
        assertEquals(1, second.setCalls.get());
    }

    @Test
    void rejectsInvalidConfiguration() {
        RedisStub redis = new RedisStub(0);
        assertThrows(IllegalArgumentException.class, () -> redis.handler(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> redis.handler(Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class, () ->
                new SingleFailedScheduleHandler(" ", redis.factory(), Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> redis.handler(null));
    }

    private static Task proxy(RedisStub redis, Callable<Object> action) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Task(action));
        factory.addAspect(new RedisScheduleAopConfig(redis.factory()));
        return factory.getProxy();
    }

    public static class Task {
        private final Callable<Object> action;

        public Task(Callable<Object> action) {
            this.action = action;
        }

        @SingleFailedSchedule(lockId = "task", aliveTime = 300)
        public Object run() throws Exception {
            return action.call();
        }
    }

    private static Map<?, ?> leases(SingleFailedScheduleHandler handler) {
        try {
            Field field = SingleFailedScheduleHandler.class.getDeclaredField("leases");
            field.setAccessible(true);
            return (Map<?, ?>) field.get(handler);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static Thread renewalThread(SingleFailedScheduleHandler handler, String taskId) {
        try {
            Object lease = leases(handler).get(taskId);
            assertNotNull(lease);
            Field thread = lease.getClass().getDeclaredField("renewalThread");
            thread.setAccessible(true);
            return (Thread) thread.get(lease);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(5000);
        assertFalse(thread.isAlive(), "Renewal must stop");
    }

    private static final class RedisStub {
        final AtomicInteger setCalls = new AtomicInteger();
        final AtomicInteger renewCalls = new AtomicInteger();
        final AtomicInteger rejectedRenewals = new AtomicInteger();
        final AtomicInteger deleteCalls = new AtomicInteger();
        final CountDownLatch renewed;
        volatile String value;
        volatile long expiresAt;
        volatile boolean failRenewal;
        volatile boolean failAcquisition;
        volatile boolean nullAcquisition;
        volatile Thread lastRenewalThread;

        RedisStub(int renewals) {
            renewed = new CountDownLatch(renewals);
        }

        SingleFailedScheduleHandler handler(Duration ttl) {
            return new SingleFailedScheduleHandler("task", factory(), ttl);
        }

        synchronized void expire() {
            value = null;
            expiresAt = 0;
        }

        synchronized void replace(String token, Duration ttl) {
            value = token;
            expiresAt = System.nanoTime() + ttl.toNanos();
        }

        private synchronized Boolean set(byte[] token, Expiration ttl) {
            setCalls.incrementAndGet();
            if (failAcquisition) {
                throw new RedisConnectionFailureException("acquisition unavailable");
            }
            if (nullAcquisition) {
                return null;
            }
            if (value != null && System.nanoTime() < expiresAt) {
                return false;
            }
            replace(new String(token, StandardCharsets.UTF_8),
                    Duration.ofMillis(ttl.getExpirationTimeInMilliseconds()));
            return true;
        }

        private synchronized long renew(byte[][] args) {
            lastRenewalThread = Thread.currentThread();
            renewCalls.incrementAndGet();
            if (failRenewal) {
                throw new RedisConnectionFailureException("renewal unavailable");
            }
            String token = new String(args[1], StandardCharsets.UTF_8);
            if (System.nanoTime() >= expiresAt || !token.equals(value)) {
                rejectedRenewals.incrementAndGet();
                return 0;
            }
            expiresAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
                    Long.parseLong(new String(args[2], StandardCharsets.UTF_8)));
            renewed.countDown();
            return 1;
        }

        RedisConnectionFactory factory() {
            RedisConnection connection = (RedisConnection) Proxy.newProxyInstance(
                    RedisConnection.class.getClassLoader(), new Class<?>[]{RedisConnection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "set" -> set((byte[]) args[1], (Expiration) args[3]);
                        case "evalSha", "eval" -> renew((byte[][]) args[3]);
                        case "stringCommands", "scriptingCommands" -> proxy;
                        case "del" -> {
                            deleteCalls.incrementAndGet();
                            yield 1L;
                        }
                        case "toString" -> "ScheduleConnectionStub";
                        default -> method.getReturnType() == boolean.class ? false : null;
                    });
            return (RedisConnectionFactory) Proxy.newProxyInstance(
                    RedisConnectionFactory.class.getClassLoader(), new Class<?>[]{RedisConnectionFactory.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getConnection" -> connection;
                        case "toString" -> "ScheduleConnectionFactoryStub";
                        default -> method.getReturnType() == boolean.class ? false : null;
                    });
        }
    }
}
