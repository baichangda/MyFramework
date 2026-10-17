package cn.bcd.app.dp.gateway.mqtt;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.spring.vehicle.command.Request;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class GatewayCommandCorrelationTest {
    @Test
    @SuppressWarnings("unchecked")
    void requestsAreCachedByTimeAndOldResponsesDoNotRemoveNewRequests() {
        String vin = "L1234567890123456";
        int flag = PacketFlag.param_query.type;
        byte[] oldTime = {26, 10, 17, 12, 34, 56};
        Request<?, ?> current = new Request<>();
        current.vin = vin;
        current.flag = flag;
        current.content = new byte[]{26, 10, 17, 12, 34, 57};
        current.id = Request.toId(vin, flag, current.content);
        current.version = ProtocolVersion.v_2016;
        current.waitVehicleResponse = true;
        current.timeout = 30;
        Map<String, String> stored = new HashMap<>();
        ValueOperations<String, String> values = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(), new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "set" -> stored.put((String) args[0], (String) args[1]);
                    case "get" -> stored.get(args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        GatewayCommandReceiver gateway = new GatewayCommandReceiver();
        gateway.redisTemplate = new RedisTemplate<>() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return values;
            }

            @Override
            public Boolean delete(String key) {
                return stored.remove(key) != null;
            }
        };
        gateway.gatewayProp = new GatewayProp();
        gateway.gatewayProp.mqttProduceTopicPrefix = "commands/";
        gateway.client = (Mqtt5AsyncClient) Proxy.newProxyInstance(Mqtt5AsyncClient.class.getClassLoader(),
                new Class<?>[]{Mqtt5AsyncClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("publish")) {
                        return new CompletableFuture<>();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        gateway.onRequest(current);
        String key = GatewayCommandReceiver.getRedisKey(current.id);
        assertEquals(current.id, JsonUtil.MAPPER.readValue(stored.get(key), Request.class).id);

        byte[] oldPacket = cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil.build_bytes_packetData(
                vin, PacketFlag.param_query, 1, oldTime);
        gateway.onResponse(vin, flag, oldPacket);
        assertEquals(1, stored.size());
        assertTrue(stored.containsKey(key));
    }

    @Test
    void bothProtocolHandlersForwardTheCompletePacket() throws Exception {
        byte[] packet = new byte[31];
        GatewayCommandReceiver gateway = new GatewayCommandReceiver() {
            @Override
            public void onResponse(String vin, int flag, byte[] bytes) {
                assertSame(packet, bytes, "Time and response parsing require the complete packet");
            }
        };
        var handler2016 = new cn.bcd.app.dp.gateway.mqtt.v2016.CommandResponseHandler_v2016();
        Field field2016 = handler2016.getClass().getDeclaredField("gatewayCommandReceiver");
        field2016.setAccessible(true);
        field2016.set(handler2016, gateway);
        handler2016.handle("L1234567890123456", PacketFlag.param_query, packet, null);

        var handler2025 = new cn.bcd.app.dp.gateway.mqtt.v2025.CommandResponseHandler_v2025();
        Field field2025 = handler2025.getClass().getDeclaredField("gatewayCommandReceiver");
        field2025.setAccessible(true);
        field2025.set(handler2025, gateway);
        handler2025.handle("L1234567890123456",
                cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag.param_query, packet, null);
    }
}
