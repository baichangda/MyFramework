package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.retained.InMemoryMqttRetainedMessageStore;
import cn.bcd.app.mqtt.server.retained.MqttRetainedMessageStore;
import cn.bcd.app.mqtt.server.session.persistence.InMemoryMqttSessionStore;
import cn.bcd.app.mqtt.server.session.persistence.MqttSessionStore;
import cn.bcd.app.mqtt.server.support.MqttTestChannel;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MqttAsyncSessionPersistenceTest {

    @Test
    void shouldAcknowledgeRetainedPublishOnlyAfterWriteThroughCompletes() {
        MqttRetainedMessageStore retainedStore = mock(MqttRetainedMessageStore.class);
        CompletableFuture<Void> retainedSaved = new CompletableFuture<>();
        when(retainedStore.save(any())).thenReturn(retainedSaved);
        MqttBroker broker = new MqttBroker(
                retainedStore, new InMemoryMqttSessionStore());
        EmbeddedChannel channel = MqttTestChannel.connect(broker, "cid", true);

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
                0x33, 0x06,
                0x00, 0x01, 't',
                0x00, 0x07,
                'x'
        }));
        assertTrue(channel.outboundMessages().isEmpty());

        retainedSaved.complete(null);
        channel.runPendingTasks();
        assertArrayEquals(
                new byte[]{0x40, 0x02, 0x00, 0x07},
                MqttTestChannel.readOutbound(channel));
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldAcknowledgeOnlyAfterDurableStateCompletes() {
        MqttSessionStore store = mock(MqttSessionStore.class);
        CompletableFuture<Void> sessionSaved = new CompletableFuture<>();
        CompletableFuture<Void> qosTwoSaved = new CompletableFuture<>();
        when(store.loadAll()).thenReturn(List.of());
        when(store.upsertSession(anyString(), any(), anyInt()))
                .thenReturn(sessionSaved);
        when(store.upsertInboundQosTwo(anyString(), any()))
                .thenReturn(qosTwoSaved);
        MqttBroker broker = new MqttBroker(
                new InMemoryMqttRetainedMessageStore(), store);
        EmbeddedChannel channel = MqttTestChannel.open(broker);

        channel.writeInbound(Unpooled.wrappedBuffer(
                MqttTestChannel.connectPacket("cid", false)));
        assertTrue(channel.outboundMessages().isEmpty());

        sessionSaved.complete(null);
        channel.runPendingTasks();
        assertArrayEquals(
                new byte[]{0x20, 0x02, 0x00, 0x00},
                MqttTestChannel.readOutbound(channel));

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
                0x34, 0x06,
                0x00, 0x01, 't',
                0x00, 0x07,
                'x'
        }));
        assertTrue(channel.outboundMessages().isEmpty());

        qosTwoSaved.complete(null);
        channel.runPendingTasks();
        assertArrayEquals(
                new byte[]{0x50, 0x02, 0x00, 0x07},
                MqttTestChannel.readOutbound(channel));
        channel.finishAndReleaseAll();
    }
}
