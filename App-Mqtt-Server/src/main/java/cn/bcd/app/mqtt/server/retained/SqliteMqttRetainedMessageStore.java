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

/**
 * SQLite 保留消息存储。
 *
 * <p>内存主题树负责低延迟查询，SQLite 负责跨重启恢复；写操作先乐观更新索引，数据库
 * 写入失败时再恢复旧索引状态。</p>
 */
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

    /**
     * 使用默认资源上限创建 SQLite 存储。
     *
     * @param properties 持久化配置
     */
    public SqliteMqttRetainedMessageStore(MqttPersistenceProperties properties) {
        this(properties, MqttResourceLimits.defaults());
    }

    /**
     * 使用服务配置中的资源上限创建 SQLite 存储。
     *
     * @param properties 持久化配置
     * @param serverProperties 服务配置
     */
    @Autowired
    public SqliteMqttRetainedMessageStore(
            MqttPersistenceProperties properties,
            MqttServerProperties serverProperties) {
        this(properties, MqttResourceLimits.from(serverProperties.getLimits()));
    }

    /**
     * 初始化数据库连接、表结构和内存索引。
     *
     * @param properties 持久化配置
     * @param limits 资源上限
     */
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
                // SQLite 文件短时被占用时等待而不是立即失败。
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute(CREATE_TABLE_SQL);
            }
            loadIndex();
        } catch (IOException | SQLException exception) {
            throw BaseException.get(
                    "Failed to initialize SQLite retained message store", exception);
        }
    }

    /**
     * 乐观更新索引并异步保存消息，失败时恢复索引。
     *
     * @param message 保留消息
     */
    @Override
    public CompletionStage<Void> save(MqttApplicationMessage message) {
        // 先更新内存索引，使查询无需等待磁盘；异步写失败时使用旧值回滚。
        MqttApplicationMessage previous = index.put(message);
        return write("save", () -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
                // topic_name 是主键，同主题保存自然覆盖载荷和 QoS。
                statement.setString(1, message.topicName());
                statement.setBytes(2, message.payload());
                statement.setInt(3, message.qos().value());
                statement.executeUpdate();
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                // 数据库失败时恢复读路径所依赖的内存真相。
                index.restorePutFailure(message, previous);
            }
        });
    }

    /**
     * 乐观删除索引消息并异步删除数据库记录，失败时恢复索引。
     *
     * @param topicName 主题名
     */
    @Override
    public CompletionStage<Void> delete(String topicName) {
        // 删除同样采用乐观更新，保证读路径始终只访问内存索引。
        MqttApplicationMessage previous = index.remove(topicName);
        return write("delete", () -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
                statement.setString(1, topicName);
                statement.executeUpdate();
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                // 仅恢复本次删除前存在的消息；不存在时回滚为空操作。
                index.restoreDeleteFailure(topicName, previous);
            }
        });
    }

    /**
     * 从内存主题索引查询匹配消息。
     *
     * @param topicFilter 主题过滤器
     */
    @Override
    public Collection<MqttApplicationMessage> findMatching(String topicFilter) {
        return index.findMatching(topicFilter);
    }

    /** 排空异步写队列并关闭数据库连接。 */
    @Override
    public void close() {
        // 等待已排队的写任务完成后再关闭 JDBC 连接。
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

    /** 在启动时将全部数据库记录恢复到内存索引。 */
    private void loadIndex() throws SQLException {
        // 启动阶段同步重建完整索引，服务开始监听后查询无需访问 SQLite。
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

    /**
     * 在单线程写执行器中提交 SQL 操作。
     *
     * @param operation 操作名称
     * @param sql SQL 操作
     */
    private CompletionStage<Void> write(String operation, SqlOperation sql) {
        // 单线程执行器保证共享 JDBC 连接不会被并发访问。
        return CompletableFuture.runAsync(() -> {
            try {
                sql.run();
            } catch (SQLException exception) {
                throw storeFailure(operation, exception);
            }
        }, writer);
    }

    /**
     * 为文件数据库创建父目录，内存数据库不执行文件操作。
     *
     * @param databasePath 数据库路径
     */
    private static void createParentDirectory(String databasePath) throws IOException {
        if (":memory:".equals(databasePath)) {
            return;
        }
        Path parent = Path.of(databasePath).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * 将 SQL 异常包装为包含操作名称的统一存储异常。
     *
     * @param operation 操作名称
     * @param exception SQL 异常
     */
    private static BaseException storeFailure(
            String operation,
            SQLException exception) {
        return BaseException.get(
                "Failed to " + operation + " retained MQTT message", exception);
    }

    @FunctionalInterface
    private interface SqlOperation {
        /** 执行一次可能抛出 SQL 异常的数据库操作。 */
        void run() throws SQLException;
    }
}
