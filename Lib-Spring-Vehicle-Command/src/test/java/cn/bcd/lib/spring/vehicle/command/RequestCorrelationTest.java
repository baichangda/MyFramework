package cn.bcd.lib.spring.vehicle.command;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.HexUtil;
import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RequestCorrelationTest {
    private static final String VIN = "L1234567890123456";
    private static final int FLAG = PacketFlag.param_query.type;
    private static final byte[] CONTENT = {26, 10, 17, 12, 34, 56};

    @Test
    void requestIdUsesTheSameEncodingThatWillBeSent() throws Exception {
        AtomicInteger encodings = new AtomicInteger();
        Command<Void, Void> command = new Command<>(null, FLAG, ProtocolVersion.v_2016) {
            @Override
            public byte[] toRequestBytes() {
                byte[] content = CONTENT.clone();
                content[5] += (byte) encodings.getAndIncrement();
                return content;
            }

            @Override
            public Void toResponse(byte[] content) {
                return null;
            }
        };
        Request<Void, Void> request = Request.create(VIN, command, 30, response -> {}, true);
        byte[] packet = request.toPacketBytes();
        assertEquals(1, encodings.get());
        assertEquals(Request.toIdFromPacket(VIN, FLAG, packet), request.id);

        Request<?, ?> decoded = JsonUtil.MAPPER.readValue(JsonUtil.toJson(request), Request.class);
        assertEquals(request.id, decoded.id);
        assertArrayEquals(packet, decoded.toPacketBytes());
    }

    @Test
    void idUsesProtocolTimeVinAndFlag() {
        long seconds = LocalDateTime.of(2026, 10, 17, 12, 34, 56).toEpochSecond(DateUtil.ZONE_OFFSET);
        String id = Request.toId(VIN, FLAG, CONTENT);
        assertEquals(seconds + "," + VIN + "," + FLAG, id);
        byte[] nextSecond = CONTENT.clone();
        nextSecond[5]++;
        assertNotEquals(id, Request.toId(VIN, FLAG, nextSecond));
        assertNotEquals(id, Request.toId("L2234567890123456", FLAG, CONTENT));
        assertNotEquals(id, Request.toId(VIN, FLAG + 1, CONTENT));
    }

    @Test
    void completePacketsAndTheirDataUnitsProduceTheSameIdInBothVersions() {
        String id = Request.toId(VIN, FLAG, CONTENT);
        byte[] packet2016 = cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil.build_bytes_packetData(
                VIN, PacketFlag.param_query, 1, CONTENT);
        byte[] packet2025 = cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil.build_bytes_packetData(
                VIN, cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag.param_query, 1, CONTENT);
        assertEquals(id, Request.toIdFromPacket(VIN, FLAG, packet2016));
        assertEquals(id, Request.toIdFromPacket(VIN, FLAG, packet2025));
        assertThrows(IllegalArgumentException.class, () -> Request.toId(VIN, FLAG, new byte[5]));
        assertThrows(IllegalArgumentException.class, () -> Request.toIdFromPacket(VIN, FLAG, new byte[29]));
    }

    @Test
    void lateKafkaResponseDoesNotCompleteOrUnlockTheNextRequest() throws Exception {
        ScheduledExecutorService previousExecutor = CommandSender.workExecutor;
        Map<String, Request<?, ?>> previousRequests = CommandSender.requestMap;
        RedisTemplate<String, String> previousRedis = CommandSender.redisTemplate;
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        List<String> deletedLocks = new ArrayList<>();
        AtomicInteger callbacks = new AtomicInteger();
        CommandResponseConsumer consumer = new CommandResponseConsumer(new CommandProp());
        try {
            CommandSender.workExecutor = executor;
            CommandSender.requestMap = new HashMap<>();
            CommandSender.redisTemplate = new RedisTemplate<>() {
                @Override
                public Boolean delete(String key) {
                    deletedLocks.add(key);
                    return true;
                }
            };
            byte[] nextSecond = CONTENT.clone();
            nextSecond[5]++;
            Request<String, String> current = new Request<>();
            current.id = Request.toId(VIN, FLAG, nextSecond);
            current.vin = VIN;
            current.flag = FLAG;
            current.callback = response -> callbacks.incrementAndGet();
            current.timeoutFuture = executor.schedule(() -> {}, 1, TimeUnit.DAYS);
            CommandSender.requestMap.put(current.id, current);
            byte[] response = JsonUtil.toJsonAsBytes(new Response<>(VIN, FLAG, ResponseStatus.success,
                    ProtocolVersion.v_2016, null));

            consumer.onMessage(new ConsumerRecord<>("responses", 0, 0,
                    Request.toId(VIN, FLAG, CONTENT), response), null);
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertSame(current, CommandSender.requestMap.get(current.id));
            assertFalse(current.timeoutFuture.isCancelled());
            assertEquals(0, callbacks.get());
            assertTrue(deletedLocks.isEmpty());

            consumer.onMessage(new ConsumerRecord<>("responses", 0, 1, current.id, response), null);
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertTrue(CommandSender.requestMap.isEmpty());
            assertTrue(current.timeoutFuture.isCancelled());
            assertEquals(1, callbacks.get());
            assertEquals(List.of(CommandSender.REDIS_KEY_PREFIX_COMMAND_LOCK + VIN + ","
                    + HexUtil.hexDump((byte) FLAG)), deletedLocks);
        } finally {
            consumer.close();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            CommandSender.workExecutor = previousExecutor;
            CommandSender.requestMap = previousRequests;
            CommandSender.redisTemplate = previousRedis;
        }
    }
}
