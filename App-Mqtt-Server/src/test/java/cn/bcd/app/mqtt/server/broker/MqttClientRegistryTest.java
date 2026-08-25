package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.config.MqttResourceLimits;
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
import static org.mockito.Mockito.when;

class MqttClientRegistryTest {

    private static final MqttResourceLimits ONE_CLIENT = new MqttResourceLimits(
            1, 10, 10, 10, 1024, 10, 1024);

    @Test
    void shouldRestorePersistentSessionsAndSubscriptionIndex() {
        InMemoryMqttSessionStore store = new InMemoryMqttSessionStore();
        MqttSession session = new MqttSession("cid", "device");
        session.subscribe(new MqttSubscription("sensor/+", MqttQoS.AT_LEAST_ONCE));
        store.upsertSession("cid", "device", session.nextPacketId());
        store.upsertSubscription("cid", session.subscriptions().iterator().next());

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
                first, "cid", "device", false).toCompletableFuture().join();

        MqttClientRegistry.MqttClientRegistration resumed = registry.connect(
                second, "cid", "device", false).toCompletableFuture().join();

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
                mock(MqttConnection.class), "cid", "first", false)
                .toCompletableFuture().join();
        initial.result().session().subscribe(
                new MqttSubscription("sensor/#", MqttQoS.AT_LEAST_ONCE));
        store.upsertSubscription(
                "cid", initial.result().session().subscriptions().iterator().next());

        MqttClientRegistry.MqttClientRegistration replaced = registry.connect(
                mock(MqttConnection.class), "cid", "second", false)
                .toCompletableFuture().join();

        assertFalse(replaced.result().sessionPresent());
        assertNotSame(initial.result().session(), replaced.result().session());
        assertTrue(replaced.result().session().subscriptions().isEmpty());
        assertTrue(store.loadAll().stream()
                .allMatch(snapshot -> "second".equals(snapshot.username())));
    }

    @Test
    void shouldLimitNewClientIdsButAllowExistingClientToReconnect() {
        MqttClientRegistry registry = new MqttClientRegistry(
                new InMemoryMqttSessionStore(), ONE_CLIENT);
        MqttConnection first = mock(MqttConnection.class);

        MqttClientRegistry.MqttClientRegistration initial = registry.connect(
                first, "first", null, false).toCompletableFuture().join();
        MqttClientRegistry.MqttClientRegistration resumed = registry.connect(
                mock(MqttConnection.class), "first", null, false)
                .toCompletableFuture().join();
        MqttClientRegistry.MqttClientRegistration rejected = registry.connect(
                mock(MqttConnection.class), "second", null, false)
                .toCompletableFuture().join();

        assertTrue(initial.result().accepted());
        assertTrue(resumed.result().accepted());
        assertTrue(resumed.result().sessionPresent());
        assertFalse(rejected.result().accepted());
        assertEquals(1, registry.size());
    }

    @Test
    void shouldReleaseClientIdCapacityWhenCleanSessionDisconnects() {
        MqttClientRegistry registry = new MqttClientRegistry(
                new InMemoryMqttSessionStore(), ONE_CLIENT);
        MqttConnection first = mock(MqttConnection.class);
        MqttClientRegistry.MqttClientRegistration initial = registry.connect(
                first, "first", null, true).toCompletableFuture().join();
        when(first.session()).thenReturn(initial.result().session());

        registry.disconnect(first);
        MqttClientRegistry.MqttClientRegistration second = registry.connect(
                mock(MqttConnection.class), "second", null, true)
                .toCompletableFuture().join();

        assertTrue(second.result().accepted());
        assertEquals(1, registry.size());
    }
}
