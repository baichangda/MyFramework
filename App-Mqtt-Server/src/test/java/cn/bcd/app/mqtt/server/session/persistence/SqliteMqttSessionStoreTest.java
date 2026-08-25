package cn.bcd.app.mqtt.server.session.persistence;

import cn.bcd.app.mqtt.server.config.MqttPersistenceProperties;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.session.MqttOutboundPublishState;
import cn.bcd.app.mqtt.server.session.MqttPendingPublish;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSessionSnapshot;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteMqttSessionStoreTest {

    @Test
    void shouldPersistCompleteSessionSnapshotAndDeleteIt() throws IOException {
        Path databasePath = databasePath();
        MqttPersistenceProperties properties = properties(databasePath);
        MqttSession session = session();

        try {
            try (SqliteMqttSessionStore first = new SqliteMqttSessionStore(properties)) {
                persist(first, session.snapshot());
            }

            try (SqliteMqttSessionStore second = new SqliteMqttSessionStore(properties)) {
                MqttSession restored = MqttSession.restore(
                        second.loadAll().stream().findFirst().orElseThrow());
                assertEquals("cid", restored.clientId());
                assertEquals("device", restored.username());
                assertEquals(2, restored.subscriptions().size());
                assertEquals(MqttQoS.AT_LEAST_ONCE, restored
                        .findSubscription("sensor/#").orElseThrow().qos());

                MqttPendingPublish qosOne = restored.pendingPublishes().stream()
                        .filter(pending -> pending.packetId() == 1)
                        .findFirst()
                        .orElseThrow();
                assertTrue(qosOne.sent());
                assertEquals(MqttOutboundPublishState.WAIT_PUBACK, qosOne.state());
                assertArrayEquals(new byte[]{0x01}, qosOne.message().payload());

                MqttPendingPublish qosTwo = restored.pendingPublishes().stream()
                        .filter(pending -> pending.packetId() == 2)
                        .findFirst()
                        .orElseThrow();
                assertEquals(MqttOutboundPublishState.WAIT_PUBCOMP, qosTwo.state());
                assertEquals(1, restored.snapshot().inboundQosTwoPublishes().size());

                MqttPendingPublish next = restored.enqueue(
                        new MqttApplicationMessage(
                                "sensor/next", new byte[]{0x04}, MqttQoS.AT_LEAST_ONCE),
                        MqttQoS.AT_LEAST_ONCE,
                        false);
                assertEquals(3, next.packetId());
                second.deleteSession("cid").toCompletableFuture().join();
            }

            try (SqliteMqttSessionStore third = new SqliteMqttSessionStore(properties)) {
                assertTrue(third.loadAll().isEmpty());
            }
        } finally {
            Files.deleteIfExists(databasePath);
        }
    }

    private static MqttSession session() {
        MqttSession session = new MqttSession("cid", "device");
        session.subscribe(new MqttSubscription("sensor/#", MqttQoS.AT_LEAST_ONCE));
        session.subscribe(new MqttSubscription("event/+", MqttQoS.EXACTLY_ONCE));
        MqttPendingPublish qosOne = session.enqueue(
                new MqttApplicationMessage(
                        "sensor/temp", new byte[]{0x01}, MqttQoS.AT_LEAST_ONCE),
                MqttQoS.AT_LEAST_ONCE,
                false);
        session.markPendingPublishSent(qosOne.packetId());
        MqttPendingPublish qosTwo = session.enqueue(
                new MqttApplicationMessage(
                        "event/status", new byte[]{0x02}, MqttQoS.EXACTLY_ONCE),
                MqttQoS.EXACTLY_ONCE,
                true);
        session.receivePubRec(qosTwo.packetId());
        session.receiveQosTwo(
                11,
                new MqttApplicationMessage(
                        "command/run", new byte[]{0x03}, MqttQoS.EXACTLY_ONCE),
                false,
                false);
        return session;
    }

    private static void persist(
            MqttSessionStore store,
            MqttSessionSnapshot snapshot) {
        store.upsertSession(
                snapshot.clientId(), snapshot.username(), snapshot.nextPacketId())
                .toCompletableFuture().join();
        snapshot.subscriptions().forEach(subscription -> store
                .upsertSubscription(snapshot.clientId(), subscription)
                .toCompletableFuture().join());
        snapshot.pendingPublishes().forEach(pending -> store
                .upsertPendingPublish(snapshot.clientId(), snapshot.nextPacketId(), pending)
                .toCompletableFuture().join());
        snapshot.inboundQosTwoPublishes().forEach(inbound -> store
                .upsertInboundQosTwo(snapshot.clientId(), inbound)
                .toCompletableFuture().join());
    }

    private static Path databasePath() {
        return Path.of("build", "tmp", "mqtt-session-" + UUID.randomUUID() + ".db");
    }

    private static MqttPersistenceProperties properties(Path databasePath) {
        MqttPersistenceProperties properties = new MqttPersistenceProperties();
        properties.getSession().getSqlite().setDatabasePath(databasePath.toString());
        return properties;
    }
}
