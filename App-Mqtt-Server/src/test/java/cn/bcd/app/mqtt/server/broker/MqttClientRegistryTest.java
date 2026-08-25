package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import cn.bcd.app.mqtt.server.session.persistence.InMemoryMqttSessionStore;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MqttClientRegistryTest {

    @Test
    void shouldRestorePersistentSessionsAndSubscriptionIndex() {
        InMemoryMqttSessionStore store = new InMemoryMqttSessionStore();
        MqttSession session = new MqttSession("cid", "device");
        session.subscribe(new MqttSubscription("sensor/+", MqttQoS.AT_LEAST_ONCE));
        store.save(session.snapshot());

        MqttClientRegistry registry = new MqttClientRegistry(store);

        assertEquals(session.clientId(), registry.findSession("cid").orElseThrow().clientId());
        assertTrue(registry.findConnection("cid").isEmpty());
        assertTrue(registry.findSubscribers("sensor/temp").containsKey("cid"));
    }

    @Test
    void shouldResumeSameIdentityAndReplaceConnection() {
        MqttClientRegistry registry = new MqttClientRegistry(
                new InMemoryMqttSessionStore());
        MqttConnection first = mock(MqttConnection.class);
        MqttConnection second = mock(MqttConnection.class);
        MqttClientRegistry.MqttClientRegistration initial = registry.connect(
                first, "cid", "device", false);

        MqttClientRegistry.MqttClientRegistration resumed = registry.connect(
                second, "cid", "device", false);

        assertTrue(resumed.result().sessionPresent());
        assertSame(initial.result().session(), resumed.result().session());
        assertSame(first, resumed.previousConnection());
        assertSame(second, registry.findConnection("cid").orElseThrow());
    }

    @Test
    void shouldDiscardSessionWhenIdentityChanges() {
        InMemoryMqttSessionStore store = new InMemoryMqttSessionStore();
        MqttClientRegistry registry = new MqttClientRegistry(store);
        MqttClientRegistry.MqttClientRegistration initial = registry.connect(
                mock(MqttConnection.class), "cid", "first", false);
        initial.result().session().subscribe(
                new MqttSubscription("sensor/#", MqttQoS.AT_LEAST_ONCE));
        store.save(initial.result().session().snapshot());

        MqttClientRegistry.MqttClientRegistration replaced = registry.connect(
                mock(MqttConnection.class), "cid", "second", false);

        assertFalse(replaced.result().sessionPresent());
        assertNotSame(initial.result().session(), replaced.result().session());
        assertTrue(replaced.result().session().subscriptions().isEmpty());
        assertTrue(store.loadAll().stream()
                .allMatch(snapshot -> "second".equals(snapshot.username())));
    }
}
