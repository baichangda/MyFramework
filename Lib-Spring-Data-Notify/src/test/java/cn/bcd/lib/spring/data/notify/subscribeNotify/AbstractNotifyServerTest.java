package cn.bcd.lib.spring.data.notify.subscribeNotify;

import io.netty.util.concurrent.DefaultEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AbstractNotifyServerTest {

    @Test
    void refreshDoesNotOverwriteNewSubscription() throws Exception {
        checkChangeDuringRedisRead(false);
    }

    @Test
    void refreshDoesNotRestoreRemovedSubscription() throws Exception {
        checkChangeDuringRedisRead(true);
    }

    private void checkChangeDuringRedisRead(boolean unsubscribe) throws Exception {
        ListenerInfo listener = new ListenerInfo("order-1", "client-1", System.currentTimeMillis());
        Map<byte[], byte[]> snapshot = unsubscribe
                ? Map.of(listener.redisField().getBytes(StandardCharsets.UTF_8),
                        listener.toString().getBytes(StandardCharsets.UTF_8))
                : Map.of();
        AtomicReference<Runnable> duringRead = new AtomicReference<>();
        RedisConnection connection = (RedisConnection) Proxy.newProxyInstance(
                RedisConnection.class.getClassLoader(), new Class<?>[]{RedisConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCommands" -> proxy;
                    case "hGetAll" -> {
                        duringRead.get().run();
                        yield snapshot;
                    }
                    case "isPipelined", "isQueueing", "isClosed" -> false;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        RedisConnectionFactory factory = (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(), new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getConnection" -> connection;
                    case "getConvertPipelineAndTxResults" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        AbstractNotifyServer server = new AbstractNotifyServer(
                List.of("localhost:9092"), factory, "test", "notify-server-test") {
        };
        server.workPool = new DefaultEventExecutor();
        try {
            Thread worker = server.workPool.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
            if (unsubscribe) {
                server.blockingNum.increment();
                server.onMessage(message('1', listener));
                server.workPool.submit(() -> {}).get(5, TimeUnit.SECONDS);
            }
            duringRead.set(() -> {
                server.blockingNum.increment();
                server.onMessage(message(unsubscribe ? '2' : '1', listener));
                // Force the old race: a change finishes while the scheduler is reading Redis.
                // With the fix, the read owns the worker and the change remains queued.
                if (Thread.currentThread() != worker) {
                    try {
                        server.workPool.submit(() -> {}).get(5, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
            Method refresh = AbstractNotifyServer.class.getDeclaredMethod("checkAndUpdateCache");
            refresh.setAccessible(true);
            CompletableFuture<?> result = (CompletableFuture<?>) refresh.invoke(server);
            result.get(5, TimeUnit.SECONDS);
            server.workPool.submit(() -> {}).get(5, TimeUnit.SECONDS);

            Field cacheField = AbstractNotifyServer.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            Map<?, ?> cache = (Map<?, ?>) cacheField.get(server);
            if (unsubscribe) {
                assertFalse(cache.containsKey(listener.id));
            } else {
                assertEquals(Set.of(listener.clientId), cache.get(listener.id));
            }
        } finally {
            try {
                server.destroy();
            } finally {
                Field producerField = AbstractNotifyServer.class.getDeclaredField("producer");
                producerField.setAccessible(true);
                ((Producer<?, ?>) producerField.get(server)).close();
            }
        }
    }

    private ConsumerRecord<String, byte[]> message(char flag, ListenerInfo listener) {
        return new ConsumerRecord<>("_subscribe_test", 0, 0, listener.id,
                (flag + listener.toString()).getBytes(StandardCharsets.UTF_8));
    }
}
