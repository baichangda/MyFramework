package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.support.MqttTestBroker;
import cn.bcd.app.mqtt.server.support.MqttTestChannel;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttSessionTest {

    @Test
    void shouldKeepPendingPublishStateInsideSession() {
        MqttSession session = new MqttSession("cid");
        MqttPendingPublish enqueued = session.enqueue(
                new MqttApplicationMessage(
                        "sensor/temp",
                        new byte[]{0x01},
                        MqttQoS.AT_LEAST_ONCE),
                MqttQoS.AT_LEAST_ONCE,
                false);

        assertTrue(session.markPendingPublishSent(enqueued.packetId()));

        assertFalse(enqueued.sent());
        assertTrue(session.pendingPublishes().stream()
                .findFirst()
                .orElseThrow()
                .sent());
        assertFalse(session.markPendingPublishSent(enqueued.packetId()));
    }

    @Test
    void shouldOnlyAcknowledgeExpectedOutboundState() {
        MqttSession session = new MqttSession("cid");
        MqttPendingPublish qosOne = session.enqueue(
                new MqttApplicationMessage(
                        "sensor/temp", new byte[0], MqttQoS.AT_LEAST_ONCE),
                MqttQoS.AT_LEAST_ONCE,
                false);
        MqttPendingPublish qosTwo = session.enqueue(
                new MqttApplicationMessage(
                        "sensor/temp", new byte[0], MqttQoS.EXACTLY_ONCE),
                MqttQoS.EXACTLY_ONCE,
                false);

        assertFalse(session.acknowledgeQosOne(qosTwo.packetId()));
        assertFalse(session.receivePubComp(qosTwo.packetId()));
        assertTrue(session.receivePubRec(qosTwo.packetId()).isPresent());
        assertTrue(session.receivePubComp(qosTwo.packetId()));
        assertTrue(session.acknowledgeQosOne(qosOne.packetId()));
        assertTrue(session.pendingPublishes().isEmpty());
    }

    @Test
    void shouldResumePersistentSessionAndSetSessionPresent() {
        MqttBroker broker = MqttTestBroker.create();

        TestClient first = connect(false, broker);
        MqttSession firstSession = first.connection().session();
        assertEquals(0, first.connAck()[2]);
        first.channel().close().syncUninterruptibly();
        assertSame(firstSession, broker.findSession("cid").orElseThrow());

        TestClient second = connect(false, broker);
        assertEquals(1, second.connAck()[2]);
        assertSame(firstSession, second.connection().session());

        second.channel().close().syncUninterruptibly();
        first.channel().finishAndReleaseAll();
        second.channel().finishAndReleaseAll();
    }

    @Test
    void shouldDiscardPreviousSessionWhenCleanSessionIsTrue() {
        MqttBroker broker = MqttTestBroker.create();
        TestClient persistent = connect(false, broker);
        MqttSession previousSession = persistent.connection().session();

        TestClient clean = connect(true, broker);
        persistent.channel().runPendingTasks();

        assertEquals(0, clean.connAck()[2]);
        assertNotSame(previousSession, clean.connection().session());
        assertSame(clean.connection().session(), broker.findSession("cid").orElseThrow());

        clean.channel().close().syncUninterruptibly();
        assertTrue(broker.findSession("cid").isEmpty());
        persistent.channel().finishAndReleaseAll();
        clean.channel().finishAndReleaseAll();
    }

    @Test
    void shouldRemoveCleanSessionWhenConnectionCloses() {
        MqttBroker broker = MqttTestBroker.create();
        TestClient client = connect(true, broker);

        client.channel().close().syncUninterruptibly();

        assertEquals(0, broker.size());
        client.channel().finishAndReleaseAll();
    }

    private static TestClient connect(
            boolean cleanSession,
            MqttBroker broker) {
        EmbeddedChannel channel = MqttTestChannel.open(broker);
        MqttConnection connection = MqttTestChannel.connection(channel);
        channel.writeInbound(Unpooled.wrappedBuffer(
                MqttTestChannel.connectPacket("cid", cleanSession)));
        return new TestClient(
                channel,
                connection,
                MqttTestChannel.readOutbound(channel));
    }

    private record TestClient(
            EmbeddedChannel channel,
            MqttConnection connection,
            byte[] connAck
    ) {
    }
}
