package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.config.MqttPersistenceProperties;
import cn.bcd.app.mqtt.server.config.MqttResourceLimits;
import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.lib.base.exception.BaseException;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(
        prefix = "mqtt.server.persistence.retained-message",
        name = "type",
        havingValue = "sqlite",
        matchIfMissing = true)
public final class SqliteMqttRetainedMessageStore
        implements MqttRetainedMessageStore, AutoCloseable {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS mqtt_retained_message (
                topic_name TEXT PRIMARY KEY,
                payload BLOB NOT NULL,
                qos INTEGER NOT NULL DEFAULT 0
            )
            """;
    private static final String UPSERT_SQL = """
            INSERT INTO mqtt_retained_message(topic_name, payload, qos) VALUES (?, ?, ?)
            ON CONFLICT(topic_name) DO UPDATE SET
                payload = excluded.payload,
                qos = excluded.qos
            """;
    private static final String DELETE_SQL =
            "DELETE FROM mqtt_retained_message WHERE topic_name = ?";
    private static final String LOAD_ALL_SQL =
            "SELECT topic_name, payload, qos FROM mqtt_retained_message";

    private final Connection connection;
    private final MqttRetainedMessageIndex index;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            Thread.ofPlatform()
                    .name("mqtt-retained-sqlite-writer")
                    .daemon()
                    .factory());

    public SqliteMqttRetainedMessageStore(MqttPersistenceProperties properties) {
        this(properties, MqttResourceLimits.defaults());
    }

    @Autowired
    public SqliteMqttRetainedMessageStore(
            MqttPersistenceProperties properties,
            MqttServerProperties serverProperties) {
        this(properties, MqttResourceLimits.from(serverProperties.getLimits()));
    }

    private SqliteMqttRetainedMessageStore(
            MqttPersistenceProperties properties,
            MqttResourceLimits limits) {
        index = new MqttRetainedMessageIndex(
                limits.retainedMessages(), limits.retainedMessageBytes());
        String databasePath = properties.getRetainedMessage().getSqlite().getDatabasePath();
        try {
            createParentDirectory(databasePath);
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute(CREATE_TABLE_SQL);
            }
            loadIndex();
        } catch (IOException | SQLException exception) {
            throw BaseException.get(
                    "Failed to initialize SQLite retained message store", exception);
        }
    }

    @Override
    public CompletionStage<Void> save(MqttApplicationMessage message) {
        MqttApplicationMessage previous = index.put(message);
        return write("save", () -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
                statement.setString(1, message.topicName());
                statement.setBytes(2, message.payload());
                statement.setInt(3, message.qos().value());
                statement.executeUpdate();
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                index.restorePutFailure(message, previous);
            }
        });
    }

    @Override
    public CompletionStage<Void> delete(String topicName) {
        MqttApplicationMessage previous = index.remove(topicName);
        return write("delete", () -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
                statement.setString(1, topicName);
                statement.executeUpdate();
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                index.restoreDeleteFailure(topicName, previous);
            }
        });
    }

    @Override
    public Collection<MqttApplicationMessage> findMatching(String topicFilter) {
        return index.findMatching(topicFilter);
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                throw BaseException.get("Timed out closing SQLite retained message store");
            }
            connection.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BaseException.get("Interrupted closing SQLite retained message store", exception);
        } catch (SQLException exception) {
            throw storeFailure("close", exception);
        }
    }

    private void loadIndex() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(LOAD_ALL_SQL)) {
            while (result.next()) {
                index.put(new MqttApplicationMessage(
                        result.getString("topic_name"),
                        result.getBytes("payload"),
                        MqttQoS.valueOf(result.getInt("qos"))));
            }
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

    private static void createParentDirectory(String databasePath) throws IOException {
        if (":memory:".equals(databasePath)) {
            return;
        }
        Path parent = Path.of(databasePath).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static BaseException storeFailure(
            String operation,
            SQLException exception) {
        return BaseException.get(
                "Failed to " + operation + " retained MQTT message", exception);
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
