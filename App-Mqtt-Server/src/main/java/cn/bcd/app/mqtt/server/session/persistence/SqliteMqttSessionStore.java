package cn.bcd.app.mqtt.server.session.persistence;

import cn.bcd.app.mqtt.server.config.MqttPersistenceProperties;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.session.MqttInboundQosTwoPublish;
import cn.bcd.app.mqtt.server.session.MqttOutboundPublishState;
import cn.bcd.app.mqtt.server.session.MqttPendingPublishSnapshot;
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
    public synchronized Collection<MqttSessionSnapshot> loadAll() {
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
    public synchronized void save(MqttSessionSnapshot snapshot) {
        try {
            connection.setAutoCommit(false);
            saveSession(snapshot);
            deleteChildren(snapshot.clientId());
            saveSubscriptions(snapshot);
            savePendingPublishes(snapshot);
            saveInboundQosTwoPublishes(snapshot);
            connection.commit();
        } catch (SQLException exception) {
            rollback(exception);
            throw storeFailure("save", exception);
        } finally {
            restoreAutoCommit();
        }
    }

    @Override
    public synchronized void delete(String clientId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM mqtt_session WHERE client_id = ?")) {
            statement.setString(1, clientId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw storeFailure("delete", exception);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
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
                    builder.pendingPublishes.add(new MqttPendingPublishSnapshot(
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

    private void saveSession(MqttSessionSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mqtt_session(client_id, username, next_packet_id) VALUES (?, ?, ?)
                ON CONFLICT(client_id) DO UPDATE SET
                    username = excluded.username,
                    next_packet_id = excluded.next_packet_id
                """)) {
            statement.setString(1, snapshot.clientId());
            statement.setString(2, snapshot.username());
            statement.setInt(3, snapshot.nextPacketId());
            statement.executeUpdate();
        }
    }

    private void deleteChildren(String clientId) throws SQLException {
        for (String table : List.of(
                "mqtt_session_subscription",
                "mqtt_session_pending_publish",
                "mqtt_session_inbound_qos_two")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE client_id = ?")) {
                statement.setString(1, clientId);
                statement.executeUpdate();
            }
        }
    }

    private void saveSubscriptions(MqttSessionSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mqtt_session_subscription(client_id, topic_filter, qos)
                VALUES (?, ?, ?)
                """)) {
            for (MqttSubscription subscription : snapshot.subscriptions()) {
                statement.setString(1, snapshot.clientId());
                statement.setString(2, subscription.topicFilter());
                statement.setInt(3, subscription.qos().value());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void savePendingPublishes(MqttSessionSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mqtt_session_pending_publish(
                    client_id, packet_id, topic_name, payload, qos, retained, sent, state
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (MqttPendingPublishSnapshot pending : snapshot.pendingPublishes()) {
                statement.setString(1, snapshot.clientId());
                statement.setInt(2, pending.packetId());
                statement.setString(3, pending.message().topicName());
                statement.setBytes(4, pending.message().payload());
                statement.setInt(5, pending.message().qos().value());
                statement.setBoolean(6, pending.retained());
                statement.setBoolean(7, pending.sent());
                statement.setString(8, pending.state().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void saveInboundQosTwoPublishes(MqttSessionSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mqtt_session_inbound_qos_two(
                    client_id, packet_id, topic_name, payload, retained
                ) VALUES (?, ?, ?, ?, ?)
                """)) {
            for (MqttInboundQosTwoPublish inbound : snapshot.inboundQosTwoPublishes()) {
                statement.setString(1, snapshot.clientId());
                statement.setInt(2, inbound.packetId());
                statement.setString(3, inbound.message().topicName());
                statement.setBytes(4, inbound.message().payload());
                statement.setBoolean(5, inbound.retained());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void rollback(SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException exception) {
            throw storeFailure("restore auto-commit for", exception);
        }
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
        private final List<MqttPendingPublishSnapshot> pendingPublishes = new ArrayList<>();
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
