package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.support.MqttTestBroker;
import cn.bcd.app.mqtt.server.support.MqttTestChannel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttConnectionTakeoverTest {

    @Test
    void shouldTakeOverExistingConnectionWithSameClientId() {
        MqttBroker broker = MqttTestBroker.create();
        TestClient first = connect(broker);
        TestClient second = connect(broker);
        first.channel().runPendingTasks();

        assertFalse(first.channel().isActive());
        assertEquals(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER,
                first.connection().closeReason());
        assertTrue(second.channel().isActive());
        assertSame(second.connection(), broker.findConnection("cid").orElseThrow());
        assertEquals(1, broker.size());

        second.channel().close().syncUninterruptibly();
        assertTrue(broker.findConnection("cid").isEmpty());
        assertEquals(0, broker.size());
        first.channel().finishAndReleaseAll();
        second.channel().finishAndReleaseAll();
    }

    @Test
    void shouldNotRemoveNewConnectionWhenOldConnectionClosesLate() {
        MqttBroker broker = MqttTestBroker.create();
        TestClient first = connect(broker);
        TestClient second = connect(broker);

        broker.disconnect(first.connection());

        assertSame(second.connection(), broker.findConnection("cid").orElseThrow());
        assertEquals(1, broker.size());

        first.channel().runPendingTasks();
        second.channel().close().syncUninterruptibly();
        first.channel().finishAndReleaseAll();
        second.channel().finishAndReleaseAll();
    }

    private static TestClient connect(MqttBroker broker) {
        EmbeddedChannel channel = MqttTestChannel.connect(broker, "cid", true);
        MqttConnection connection = MqttTestChannel.connection(channel);
        return new TestClient(channel, connection);
    }

    private record TestClient(EmbeddedChannel channel, MqttConnection connection) {
    }
}
