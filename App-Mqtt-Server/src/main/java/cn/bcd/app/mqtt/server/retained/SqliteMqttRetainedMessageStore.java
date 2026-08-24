package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.config.MqttPersistenceProperties;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;
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
import java.util.List;

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
    private static final String SAVE_SQL = """
            INSERT INTO mqtt_retained_message(topic_name, payload, qos) VALUES (?, ?, ?)
            ON CONFLICT(topic_name) DO UPDATE SET
                payload = excluded.payload,
                qos = excluded.qos
            """;
    private static final String DELETE_SQL =
            "DELETE FROM mqtt_retained_message WHERE topic_name = ?";
    private static final String FIND_EXACT_SQL =
            "SELECT topic_name, payload, qos FROM mqtt_retained_message WHERE topic_name = ?";
    private static final String FIND_ALL_SQL =
            "SELECT topic_name, payload, qos FROM mqtt_retained_message";

    private final Connection connection;

    public SqliteMqttRetainedMessageStore(MqttPersistenceProperties properties) {
        String databasePath = properties.getRetainedMessage().getSqlite().getDatabasePath();
        try {
            createParentDirectory(databasePath);
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute(CREATE_TABLE_SQL);
            }
        } catch (IOException | SQLException exception) {
            throw BaseException.get(
                    "Failed to initialize SQLite retained message store", exception);
        }
    }

    @Override
    public synchronized void save(MqttApplicationMessage message) {
        try (PreparedStatement statement = connection.prepareStatement(SAVE_SQL)) {
            statement.setString(1, message.topicName());
            statement.setBytes(2, message.payload());
            statement.setInt(3, message.qos().value());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw storeFailure("save", exception);
        }
    }

    @Override
    public synchronized void delete(String topicName) {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, topicName);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw storeFailure("delete", exception);
        }
    }

    @Override
    public synchronized Collection<MqttApplicationMessage> findMatching(String topicFilter) {
        boolean exact = topicFilter.indexOf('+') < 0 && topicFilter.indexOf('#') < 0;
        String sql = exact ? FIND_EXACT_SQL : FIND_ALL_SQL;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (exact) {
                statement.setString(1, topicFilter);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MqttApplicationMessage> matches = new ArrayList<>();
                while (resultSet.next()) {
                    String topicName = resultSet.getString("topic_name");
                    if (exact || MqttTopicFilter.matches(topicFilter, topicName)) {
                        matches.add(new MqttApplicationMessage(
                                topicName,
                                resultSet.getBytes("payload"),
                                MqttQoS.valueOf(resultSet.getInt("qos"))));
                    }
                }
                return List.copyOf(matches);
            }
        } catch (SQLException exception) {
            throw storeFailure("query", exception);
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
}
