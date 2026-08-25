package cn.bcd.app.mqtt.server.session.persistence;

import cn.bcd.app.mqtt.server.config.MqttPersistenceProperties;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.session.MqttInboundQosTwoPublish;
import cn.bcd.app.mqtt.server.session.MqttOutboundPublishState;
import cn.bcd.app.mqtt.server.session.MqttPendingPublish;
import cn.bcd.app.mqtt.server.session.MqttSessionSnapshot;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import cn.bcd.lib.base.exception.BaseException;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(
        prefix = "mqtt.server.persistence.session",
        name = "type",
        havingValue = "sqlite",
        matchIfMissing = true)
public final class SqliteMqttSessionStore implements MqttSessionStore, AutoCloseable {

    private static final String CREATE_SESSION_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS mqtt_session (
                client_id TEXT PRIMARY KEY,
                username TEXT,
                next_packet_id INTEGER NOT NULL CHECK(next_packet_id BETWEEN 1 AND 65535)
            )
            """;
    private static final String CREATE_SUBSCRIPTION_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS mqtt_session_subscription (
                client_id TEXT NOT NULL,
                topic_filter TEXT NOT NULL,
                qos INTEGER NOT NULL CHECK(qos BETWEEN 0 AND 2),
                PRIMARY KEY(client_id, topic_filter),
                FOREIGN KEY(client_id) REFERENCES mqtt_session(client_id) ON DELETE CASCADE
            )
            """;
    private static final String CREATE_PENDING_PUBLISH_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS mqtt_session_pending_publish (
                client_id TEXT NOT NULL,
                packet_id INTEGER NOT NULL CHECK(packet_id BETWEEN 1 AND 65535),
                topic_name TEXT NOT NULL,
                payload BLOB NOT NULL,
                qos INTEGER NOT NULL CHECK(qos IN (1, 2)),
                retained INTEGER NOT NULL CHECK(retained IN (0, 1)),
                sent INTEGER NOT NULL CHECK(sent IN (0, 1)),
                state TEXT NOT NULL,
                PRIMARY KEY(client_id, packet_id),
                FOREIGN KEY(client_id) REFERENCES mqtt_session(client_id) ON DELETE CASCADE,
                CHECK(
                    (qos = 1 AND state = 'WAIT_PUBACK') OR
                    (qos = 2 AND state IN ('WAIT_PUBREC', 'WAIT_PUBCOMP'))
                )
            )
            """;
    private static final String CREATE_INBOUND_QOS_TWO_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS mqtt_session_inbound_qos_two (
                client_id TEXT NOT NULL,
                packet_id INTEGER NOT NULL CHECK(packet_id BETWEEN 1 AND 65535),
                topic_name TEXT NOT NULL,
                payload BLOB NOT NULL,
                retained INTEGER NOT NULL CHECK(retained IN (0, 1)),
                PRIMARY KEY(client_id, packet_id),
                FOREIGN KEY(client_id) REFERENCES mqtt_session(client_id) ON DELETE CASCADE
            )
            """;

    private final Connection connection;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            Thread.ofPlatform()
                    .name("mqtt-session-sqlite-writer")
                    .daemon()
                    .factory());

    public SqliteMqttSessionStore(MqttPersistenceProperties properties) {
        String databasePath = properties.getSession().getSqlite().getDatabasePath();
        try {
            createParentDirectory(databasePath);
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute(CREATE_SESSION_TABLE_SQL);
                statement.execute(CREATE_SUBSCRIPTION_TABLE_SQL);
                statement.execute(CREATE_PENDING_PUBLISH_TABLE_SQL);
                statement.execute(CREATE_INBOUND_QOS_TWO_TABLE_SQL);
            }
        } catch (IOException | SQLException exception) {
            throw BaseException.get("Failed to initialize SQLite MQTT session store", exception);
        }
    }

    @Override
    public Collection<MqttSessionSnapshot> loadAll() {
        try {
            Map<String, SnapshotBuilder> builders = loadSessions();
            loadSubscriptions(builders);
            loadPendingPublishes(builders);
            loadInboundQosTwoPublishes(builders);
            return builders.values().stream()
                    .map(SnapshotBuilder::build)
                    .toList();
        } catch (SQLException | IllegalArgumentException exception) {
            throw storeFailure("load", exception);
        }
    }

    @Override
    public CompletionStage<Void> upsertSession(
            String clientId,
            String username,
            int nextPacketId) {
        return write("upsert", () -> upsertSessionRow(clientId, username, nextPacketId));
    }

    @Override
    public CompletionStage<Void> deleteSession(String clientId) {
        return write("delete", () -> deleteByClientId(
                "mqtt_session", clientId));
    }

    @Override
    public CompletionStage<Void> upsertSubscription(
            String clientId,
            MqttSubscription subscription) {
        return write("upsert subscription for", () -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO mqtt_session_subscription(client_id, topic_filter, qos)
                    VALUES (?, ?, ?)
                    ON CONFLICT(client_id, topic_filter) DO UPDATE SET qos = excluded.qos
                    """)) {
                statement.setString(1, clientId);
                statement.setString(2, subscription.topicFilter());
                statement.setInt(3, subscription.qos().value());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletionStage<Void> deleteSubscriptions(
            String clientId,
            Collection<String> topicFilters) {
        return write("delete subscriptions from", () -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM mqtt_session_subscription
                    WHERE client_id = ? AND topic_filter = ?
                    """)) {
                for (String topicFilter : topicFilters) {
                    statement.setString(1, clientId);
                    statement.setString(2, topicFilter);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    @Override
    public CompletionStage<Void> upsertPendingPublish(
            String clientId,
            int nextPacketId,
            MqttPendingPublish pending) {
        return write("upsert pending publish for", () -> inTransaction(() -> {
            updateNextPacketId(clientId, nextPacketId);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO mqtt_session_pending_publish(
                        client_id, packet_id, topic_name, payload, qos, retained, sent, state
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(client_id, packet_id) DO UPDATE SET
                        topic_name = excluded.topic_name,
                        payload = excluded.payload,
                        qos = excluded.qos,
                        retained = excluded.retained,
                        sent = excluded.sent,
                        state = excluded.state
                    """)) {
                statement.setString(1, clientId);
                statement.setInt(2, pending.packetId());
                statement.setString(3, pending.message().topicName());
                statement.setBytes(4, pending.message().payload());
                statement.setInt(5, pending.message().qos().value());
                statement.setBoolean(6, pending.retained());
                statement.setBoolean(7, pending.sent());
                statement.setString(8, pending.state().name());
                statement.executeUpdate();
            }
        }));
    }

    @Override
    public CompletionStage<Void> deletePendingPublish(String clientId, int packetId) {
        return write("delete pending publish from", () -> deleteByPacketId(
                "mqtt_session_pending_publish", clientId, packetId));
    }

    @Override
    public CompletionStage<Void> upsertInboundQosTwo(
            String clientId,
            MqttInboundQosTwoPublish publish) {
        return write("upsert inbound QoS 2 publish for", () -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO mqtt_session_inbound_qos_two(
                        client_id, packet_id, topic_name, payload, retained
                    ) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(client_id, packet_id) DO UPDATE SET
                        topic_name = excluded.topic_name,
                        payload = excluded.payload,
                        retained = excluded.retained
                    """)) {
                statement.setString(1, clientId);
                statement.setInt(2, publish.packetId());
                statement.setString(3, publish.message().topicName());
                statement.setBytes(4, publish.message().payload());
                statement.setBoolean(5, publish.retained());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletionStage<Void> deleteInboundQosTwo(String clientId, int packetId) {
        return write("delete inbound QoS 2 publish from", () -> deleteByPacketId(
                "mqtt_session_inbound_qos_two", clientId, packetId));
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                throw BaseException.get("Timed out closing SQLite MQTT session store");
            }
            connection.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BaseException.get("Interrupted closing SQLite MQTT session store", exception);
        } catch (SQLException exception) {
            throw storeFailure("close", exception);
        }
    }

    private Map<String, SnapshotBuilder> loadSessions() throws SQLException {
        Map<String, SnapshotBuilder> builders = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT client_id, username, next_packet_id FROM mqtt_session")) {
            while (result.next()) {
                String clientId = result.getString("client_id");
                builders.put(clientId, new SnapshotBuilder(
                        clientId,
                        result.getString("username"),
                        result.getInt("next_packet_id")));
            }
        }
        return builders;
    }

    private void loadSubscriptions(Map<String, SnapshotBuilder> builders) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT client_id, topic_filter, qos FROM mqtt_session_subscription")) {
            while (result.next()) {
                SnapshotBuilder builder = builders.get(result.getString("client_id"));
                if (builder != null) {
                    builder.subscriptions.add(new MqttSubscription(
                            result.getString("topic_filter"),
                            MqttQoS.valueOf(result.getInt("qos"))));
                }
            }
        }
    }

    private void loadPendingPublishes(Map<String, SnapshotBuilder> builders) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT client_id, packet_id, topic_name, payload, qos,
                            retained, sent, state
                     FROM mqtt_session_pending_publish
                     """)) {
            while (result.next()) {
                SnapshotBuilder builder = builders.get(result.getString("client_id"));
                if (builder != null) {
                    builder.pendingPublishes.add(new MqttPendingPublish(
                            result.getInt("packet_id"),
                            new MqttApplicationMessage(
                                    result.getString("topic_name"),
                                    result.getBytes("payload"),
                                    MqttQoS.valueOf(result.getInt("qos"))),
                            result.getBoolean("retained"),
                            result.getBoolean("sent"),
                            MqttOutboundPublishState.valueOf(result.getString("state"))));
                }
            }
        }
    }

    private void loadInboundQosTwoPublishes(
            Map<String, SnapshotBuilder> builders) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT client_id, packet_id, topic_name, payload, retained
                     FROM mqtt_session_inbound_qos_two
                     """)) {
            while (result.next()) {
                SnapshotBuilder builder = builders.get(result.getString("client_id"));
                if (builder != null) {
                    builder.inboundQosTwoPublishes.add(new MqttInboundQosTwoPublish(
                            result.getInt("packet_id"),
                            new MqttApplicationMessage(
                                    result.getString("topic_name"),
                                    result.getBytes("payload"),
                                    MqttQoS.EXACTLY_ONCE),
                            result.getBoolean("retained")));
                }
            }
        }
    }

    private void upsertSessionRow(
            String clientId,
            String username,
            int nextPacketId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mqtt_session(client_id, username, next_packet_id) VALUES (?, ?, ?)
                ON CONFLICT(client_id) DO UPDATE SET
                    username = excluded.username,
                    next_packet_id = excluded.next_packet_id
                """)) {
            statement.setString(1, clientId);
            statement.setString(2, username);
            statement.setInt(3, nextPacketId);
            statement.executeUpdate();
        }
    }

    private void updateNextPacketId(String clientId, int nextPacketId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE mqtt_session SET next_packet_id = ? WHERE client_id = ?
                """)) {
            statement.setInt(1, nextPacketId);
            statement.setString(2, clientId);
            statement.executeUpdate();
        }
    }

    private void deleteByClientId(String table, String clientId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE client_id = ?")) {
            statement.setString(1, clientId);
            statement.executeUpdate();
        }
    }

    private void deleteByPacketId(
            String table,
            String clientId,
            int packetId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE client_id = ? AND packet_id = ?")) {
            statement.setString(1, clientId);
            statement.setInt(2, packetId);
            statement.executeUpdate();
        }
    }

    private CompletionStage<Void> write(String operation, SqlOperation sql) {
        return CompletableFuture.runAsync(() -> {
            try {
                sql.run();
            } catch (SQLException exception) {
                throw storeFailure(operation, exception);
            }
        }, writer);
    }

    private void inTransaction(SqlOperation sql) throws SQLException {
        connection.setAutoCommit(false);
        try {
            sql.run();
            connection.commit();
        } catch (SQLException exception) {
            rollback(exception);
            throw exception;
        } finally {
            restoreAutoCommit();
        }
    }

    private void rollback(SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private void restoreAutoCommit() throws SQLException {
        connection.setAutoCommit(true);
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }

    private static void createParentDirectory(String databasePath) throws IOException {
        if (":memory:".equals(databasePath)) {
            return;
        }
        Path parent = Path.of(databasePath).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static BaseException storeFailure(String operation, Exception exception) {
        return BaseException.get(
                "Failed to " + operation + " persistent MQTT session", exception);
    }

    private static final class SnapshotBuilder {

        private final String clientId;
        private final String username;
        private final int nextPacketId;
        private final List<MqttSubscription> subscriptions = new ArrayList<>();
        private final List<MqttPendingPublish> pendingPublishes = new ArrayList<>();
        private final List<MqttInboundQosTwoPublish> inboundQosTwoPublishes =
                new ArrayList<>();

        private SnapshotBuilder(String clientId, String username, int nextPacketId) {
            this.clientId = clientId;
            this.username = username;
            this.nextPacketId = nextPacketId;
        }

        private MqttSessionSnapshot build() {
            return new MqttSessionSnapshot(
                    clientId,
                    username,
                    nextPacketId,
                    subscriptions,
                    pendingPublishes,
                    inboundQosTwoPublishes);
        }
    }
}
