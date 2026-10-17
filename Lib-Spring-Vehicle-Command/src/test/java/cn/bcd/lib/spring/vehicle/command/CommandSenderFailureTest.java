package cn.bcd.lib.spring.vehicle.command;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CommandSenderFailureTest {
    private static final String VIN = "L1234567890123456";
    private static final int FLAG = 0x80;
    private final ScheduledExecutorService previousExecutor = CommandSender.workExecutor;
    private final Map<String, Request<?, ?>> previousRequests = CommandSender.requestMap;
    private final RedisTemplate<String, String> previousRedis = CommandSender.redisTemplate;
    private final Producer<String, byte[]> previousProducer = CommandSender.kafkaProducer;
    private final CommandProp previousProp = CommandSender.commandProp;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final MockProducer<String, byte[]> producer = new MockProducer<>(false, null, new StringSerializer(), new ByteArraySerializer());
    private final List<ResponseStatus> results = new ArrayList<>();
    private final List<String> deletedLocks = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> values = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(), new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "get" -> Long.toString(System.currentTimeMillis());
                    case "setIfAbsent" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        CommandSender.workExecutor = executor;
        CommandSender.requestMap = new HashMap<>();
        CommandSender.kafkaProducer = producer;
        CommandSender.commandProp = new CommandProp();
        CommandSender.commandProp.requestTopic = "requests";
        CommandSender.redisTemplate = new RedisTemplate<>() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return values;
            }

            @Override
            public Boolean delete(String key) {
                deletedLocks.add(key);
                return true;
            }
        };
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            producer.close();
        } finally {
            CommandSender.workExecutor = previousExecutor;
            CommandSender.requestMap = previousRequests;
            CommandSender.redisTemplate = previousRedis;
            CommandSender.kafkaProducer = previousProducer;
            CommandSender.commandProp = previousProp;
        }
    }

    @Test
    void encodingFailureNotifiesAndUnlocks() {
        CommandSender.send(VIN, command(true), 30, response -> results.add(response.status), true);
        assertCompleted(ResponseStatus.program_error);
        assertTrue(producer.history().isEmpty());
    }

    @Test
    void synchronousSendFailureNotifiesAndUnlocks() throws Exception {
        producer.sendException = new IllegalStateException("send failed");
        send(30);
        awaitWork();
        assertCompleted(ResponseStatus.program_error);
    }

    @Test
    void asynchronousSendFailureCancelsTimeoutAndNotifiesOnce() throws Exception {
        send(30);
        awaitWork();
        ScheduledFuture<?> timeout = pendingTimeout();
        assertTrue(producer.errorNext(new IllegalStateException("send failed")));
        awaitWork();
        assertTrue(timeout.isCancelled());
        assertCompleted(ResponseStatus.program_error);
    }

    @Test
    void lateSendFailureAfterTimeoutDoesNotNotifyOrUnlockAgain() throws Exception {
        send(0);
        awaitWork();
        awaitWork();
        assertCompleted(ResponseStatus.timeout);
        assertTrue(producer.errorNext(new IllegalStateException("late send failure")));
        awaitWork();
        assertCompleted(ResponseStatus.timeout);
    }

    @Test
    void lateSendFailureAfterResponseDoesNotNotifyOrUnlockAgain() throws Exception {
        send(30);
        awaitWork();
        ScheduledFuture<?> timeout = pendingTimeout();
        String id = producer.history().getFirst().key();
        CommandResponseConsumer consumer = new CommandResponseConsumer(new CommandProp());
        try {
            byte[] response = JsonUtil.toJsonAsBytes(new Response<>(VIN, FLAG, ResponseStatus.success,
                    ProtocolVersion.v_2016, null));
            consumer.onMessage(new ConsumerRecord<>("responses", 0, 0, id, response), null);
            awaitWork();
            assertTrue(timeout.isCancelled());
            assertCompleted(ResponseStatus.success);
            assertTrue(producer.errorNext(new IllegalStateException("late send failure")));
            awaitWork();
            assertCompleted(ResponseStatus.success);
        } finally {
            consumer.close();
        }
    }

    @Test
    void throwingCallbackStillUnlocks() throws Exception {
        CommandSender.send(VIN, command(false), 30, response -> {
            results.add(response.status);
            throw new IllegalStateException("callback failed");
        }, true);
        awaitWork();
        assertTrue(producer.errorNext(new IllegalStateException("send failed")));
        awaitWork();
        assertCompleted(ResponseStatus.program_error);
    }

    private void send(int timeout) {
        CommandSender.send(VIN, command(false), timeout, response -> results.add(response.status), true);
    }

    private Command<Void, Void> command(boolean failEncoding) {
        return new Command<>(null, FLAG, ProtocolVersion.v_2016) {
            @Override
            public byte[] toRequestBytes() {
                if (failEncoding) {
                    throw new IllegalArgumentException("encoding failed");
                }
                return new byte[]{26, 10, 17, 12, 34, 56};
            }

            @Override
            public Void toResponse(byte[] content) {
                return null;
            }
        };
    }

    private void awaitWork() throws Exception {
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    private ScheduledFuture<?> pendingTimeout() throws Exception {
        return executor.submit(() -> CommandSender.requestMap.values().iterator().next().timeoutFuture)
                .get(5, TimeUnit.SECONDS);
    }

    private void assertCompleted(ResponseStatus status) {
        assertEquals(List.of(status), results);
        assertEquals(List.of(CommandSender.REDIS_KEY_PREFIX_COMMAND_LOCK + VIN + ",80"), deletedLocks);
        assertTrue(CommandSender.requestMap.isEmpty());
    }
}
