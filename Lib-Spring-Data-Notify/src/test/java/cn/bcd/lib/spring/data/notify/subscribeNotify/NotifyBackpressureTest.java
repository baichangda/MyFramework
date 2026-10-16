package cn.bcd.lib.spring.data.notify.subscribeNotify;

import cn.bcd.lib.spring.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NotifyBackpressureTest {

    @Test
    void queuedMessagesKeepTheirBlockingCountOnBothSides() throws Exception {
        for (boolean client : new boolean[]{true, false}) {
            try (Fixture fixture = new Fixture(client)) {
                assertFalse(fixture.consumer.autoReleaseBlocking);
                CountDownLatch entered = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                fixture.pool.execute(() -> {
                    entered.countDown();
                    await(release);
                });
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    fixture.consumer.blockingNum.increment();
                    fixture.consumer.queue.put(record("1" + listener()));
                    assertTrue(fixture.dispatched.await(5, TimeUnit.SECONDS));
                    assertEquals(1, fixture.consumer.blockingNum.sum());
                } finally {
                    release.countDown();
                }
                fixture.drain();
                assertEquals(0, fixture.consumer.blockingNum.sum());
            }
        }
    }

    @Test
    void clientKeepsCountUntilCallbackFinishesEvenWhenCallbackThrows() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            AbstractNotifyClient client = (AbstractNotifyClient) fixture.consumer;
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            client.id_listenerInfo.put("id", new ListenerInfo("id", "client", 1, value -> {
                entered.countDown();
                await(release);
                throw new IllegalStateException("callback failure");
            }));
            try {
                client.blockingNum.increment();
                client.queue.put(record("data"));
                assertTrue(fixture.dispatched.await(5, TimeUnit.SECONDS));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(1, client.blockingNum.sum());
            } finally {
                release.countDown();
            }
            fixture.drain();
            assertEquals(0, client.blockingNum.sum());
        }
    }

    @Test
    void serverReleasesCountForInvalidAndValidMessages() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            for (String content : new String[]{null, "", "1", "1invalid", "3" + listener(),
                    "1" + listener(), "2" + listener()}) {
                fixture.consumer.blockingNum.increment();
                fixture.consumer.onMessage(record(content));
                fixture.drain();
                assertEquals(0, fixture.consumer.blockingNum.sum(), "content: " + content);
            }
        }
    }

    @Test
    void closeFromEventLoopDrainsPendingTasksWithoutWaitingForItself() throws Exception {
        for (boolean client : new boolean[]{true, false}) {
            try (Fixture fixture = new Fixture(client)) {
                CountDownLatch entered = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                var closeTask = fixture.pool.submit(() -> {
                    entered.countDown();
                    await(release);
                    if (fixture.consumer instanceof AbstractNotifyServer server) {
                        server.destroy();
                    } else {
                        fixture.consumer.close();
                    }
                });
                CompletableFuture<Void> pending;
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    pending = CompletableFuture.runAsync(() -> {}, fixture.pool);
                } finally {
                    release.countDown();
                }
                closeTask.get(5, TimeUnit.SECONDS);
                pending.get(5, TimeUnit.SECONDS);
                fixture.pool.terminationFuture().get(5, TimeUnit.SECONDS);
            }
        }
    }

    private static ListenerInfo listener() {
        return new ListenerInfo("id", "client", System.currentTimeMillis());
    }

    private static ConsumerRecord<String, byte[]> record(String content) {
        return new ConsumerRecord<>("topic", 0, 0, "id",
                content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test task");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    private static class Fixture implements AutoCloseable {
        final CountDownLatch dispatched = new CountDownLatch(1);
        final ThreadDrivenKafkaConsumer consumer;
        final SingleThreadEventExecutor pool;

        Fixture(boolean client) {
            RedisConnectionFactory factory = (RedisConnectionFactory) Proxy.newProxyInstance(
                    RedisConnectionFactory.class.getClassLoader(), new Class<?>[]{RedisConnectionFactory.class},
                    (proxy, method, args) -> {
                        throw new UnsupportedOperationException(method.getName());
                    });
            if (client) {
                AbstractNotifyClient value = new AbstractNotifyClient(
                        List.of("localhost:9092"), factory, "test", "client") {
                    @Override
                    public void onMessage(ConsumerRecord<String, byte[]> record) throws Exception {
                        try {
                            super.onMessage(record);
                        } finally {
                            dispatched.countDown();
                        }
                    }
                };
                value.workPool = new DefaultEventExecutor();
                consumer = value;
                pool = value.workPool;
            } else {
                AbstractNotifyServer value = new AbstractNotifyServer(
                        List.of("localhost:9092"), factory, "test", "server") {
                    @Override
                    public void onMessage(ConsumerRecord<String, byte[]> record) {
                        try {
                            super.onMessage(record);
                        } finally {
                            dispatched.countDown();
                        }
                    }
                };
                value.workPool = new DefaultEventExecutor();
                consumer = value;
                pool = value.workPool;
            }
        }

        void drain() throws Exception {
            pool.submit(() -> {}).get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            Class<?> owner = consumer instanceof AbstractNotifyClient
                    ? AbstractNotifyClient.class : AbstractNotifyServer.class;
            try {
                if (consumer instanceof AbstractNotifyServer server) {
                    server.destroy();
                } else {
                    consumer.close();
                }
            } finally {
                Field field = owner.getDeclaredField("producer");
                field.setAccessible(true);
                ((Producer<?, ?>) field.get(consumer)).close();
            }
        }
    }
}
