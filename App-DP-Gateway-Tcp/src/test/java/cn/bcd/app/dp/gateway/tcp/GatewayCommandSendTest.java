package cn.bcd.app.dp.gateway.tcp;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;
import cn.bcd.lib.spring.vehicle.command.CommandProp;
import cn.bcd.lib.spring.vehicle.command.CommandRequestConsumer;
import cn.bcd.lib.spring.vehicle.command.Request;
import cn.bcd.lib.spring.vehicle.command.Response;
import cn.bcd.lib.spring.vehicle.command.ResponseStatus;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GatewayCommandSendTest {
    @Test
    void failedWriteReturnsSendVehicleError() throws Exception {
        checkFailure(false);
    }

    @Test
    void encodingExceptionReturnsProgramErrorWithoutWriting() throws Exception {
        checkFailure(true);
    }

    private void checkFailure(boolean failEncoding) throws Exception {
        Field producerField = CommandRequestConsumer.class.getDeclaredField("kafkaProducer");
        Field propField = CommandRequestConsumer.class.getDeclaredField("commandProp");
        producerField.setAccessible(true);
        propField.setAccessible(true);
        Object previousProducer = producerField.get(null);
        Object previousProp = propField.get(null);
        AtomicInteger writes = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                writes.incrementAndGet();
                ReferenceCountUtil.release(message);
                promise.setFailure(new IOException("write failed"));
            }
        });
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
        request.waitVehicleResponse = true;
        request.timeout = 30;
        Session session = new Session(request.vin, channel);
        try (MockProducer<String, byte[]> producer = new MockProducer<>(true, null, new StringSerializer(), new ByteArraySerializer())) {
            producerField.set(null, producer);
            CommandProp prop = new CommandProp();
            prop.responseTopic = "responses";
            propField.set(null, prop);
            new GatewayCommandReceiver().onRequest(request);
            channel.runPendingTasks();
            assertNull(GatewayCommandReceiver.cache.getIfPresent(request.id));
            assertEquals(failEncoding ? 0 : 1, writes.get());
            assertEquals(1, producer.history().size());
            Response<?, ?> response = JsonUtil.MAPPER.readValue(producer.history().getFirst().value(), Response.class);
            assertEquals(failEncoding ? ResponseStatus.program_error : ResponseStatus.send_vehicle_error, response.status);
        } finally {
            producerField.set(null, previousProducer);
            propField.set(null, previousProp);
            Session.sessionMap.remove(request.vin, session);
            GatewayCommandReceiver.cache.invalidate(request.id);
            channel.finishAndReleaseAll();
        }
    }
}
