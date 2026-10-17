package cn.bcd.app.dp.gateway.mqtt;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;
import cn.bcd.lib.spring.vehicle.command.CommandProp;
import cn.bcd.lib.spring.vehicle.command.CommandRequestConsumer;
import cn.bcd.lib.spring.vehicle.command.Request;
import cn.bcd.lib.spring.vehicle.command.Response;
import cn.bcd.lib.spring.vehicle.command.ResponseStatus;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class GatewayCommandSendTest {
    @Test
    void asynchronousExceptionClearsPendingRequestAndReturnsProgramError() throws Exception {
        checkPublish(true, true, false);
    }

    @Test
    void asynchronousFailureDoesNotReportSuccessWhenNotWaitingForVehicle() throws Exception {
        checkPublish(false, true, false);
    }

    @Test
    void synchronousFailureClearsPendingRequestAndReturnsSendError() throws Exception {
        checkPublish(true, true, true);
    }

    @Test
    void successfulPublishReportsSuccessWhenNotWaitingForVehicle() throws Exception {
        checkPublish(false, false, false);
    }

    @Test
    void encodingExceptionReturnsProgramError() throws Exception {
        checkPublish(true, true, false, true);
    }

    private void checkPublish(boolean waitVehicleResponse, boolean fail, boolean throwSynchronously) throws Exception {
        checkPublish(waitVehicleResponse, fail, throwSynchronously, false);
    }

    @SuppressWarnings("unchecked")
    private void checkPublish(boolean waitVehicleResponse, boolean fail, boolean throwSynchronously, boolean failEncoding) throws Exception {
        Field producerField = CommandRequestConsumer.class.getDeclaredField("kafkaProducer");
        Field propField = CommandRequestConsumer.class.getDeclaredField("commandProp");
        producerField.setAccessible(true);
        propField.setAccessible(true);
        Object previousProducer = producerField.get(null);
        Object previousProp = propField.get(null);
        try (MockProducer<String, byte[]> producer = new MockProducer<>(true, null, new StringSerializer(), new ByteArraySerializer())) {
            producerField.set(null, producer);
            CommandProp prop = new CommandProp();
            prop.responseTopic = "responses";
            propField.set(null, prop);
            Request<?, ?> request = new Request<>() {
                @Override
                public byte[] toPacketBytes() {
                    if (failEncoding) {
                        throw new IllegalArgumentException("encoding failed");
                    }
                    return super.toPacketBytes();
                }
            };
            request.vin = "L1234567890123456";
            request.flag = 0x80;
            request.content = new byte[]{26, 10, 17, 12, 34, 56};
            request.id = Request.toId(request.vin, request.flag, request.content);
            request.version = ProtocolVersion.v_2016;
            request.timeout = 30;
            request.waitVehicleResponse = waitVehicleResponse;
            Map<String, String> stored = new HashMap<>();
            ValueOperations<String, String> values = (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(), new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("set")) {
                            stored.put((String) args[0], (String) args[1]);
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
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
            CompletableFuture<Object> result = new CompletableFuture<>();
            gateway.client = (Mqtt5AsyncClient) Proxy.newProxyInstance(Mqtt5AsyncClient.class.getClassLoader(),
                    new Class<?>[]{Mqtt5AsyncClient.class}, (proxy, method, args) -> {
                        if (method.getName().equals("publish")) {
                            if (throwSynchronously) {
                                throw new IllegalStateException("publish failed");
                            }
                            return result;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
            gateway.onRequest(request);
            if (!throwSynchronously && !failEncoding) {
                assertTrue(producer.history().isEmpty());
                assertEquals(waitVehicleResponse, stored.containsKey(GatewayCommandReceiver.getRedisKey(request.id)));
                if (fail) {
                    result.completeExceptionally(new IllegalStateException("publish failed"));
                } else {
                    result.complete(null);
                }
            }
            assertTrue(stored.isEmpty());
            assertEquals(1, producer.history().size());
            assertEquals(request.id, producer.history().getFirst().key());
            Response<?, ?> response = JsonUtil.MAPPER.readValue(producer.history().getFirst().value(), Response.class);
            ResponseStatus expected = throwSynchronously || failEncoding ? ResponseStatus.program_error
                    : fail ? ResponseStatus.send_vehicle_error : ResponseStatus.success;
            assertEquals(expected, response.status);
            assertSame(ResponseStatus.send_vehicle_error, ResponseStatus.from(5));
        } finally {
            producerField.set(null, previousProducer);
            propField.set(null, previousProp);
        }
    }
}
