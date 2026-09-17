package cn.bcd.lib.spring.database.jdbc.sqlite;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 基于 Spring JDBC 的 SQLite 轻量入口。
 *
 * <p>文件数据库由 {@link SQLiteDataSource} 按操作获取连接；内存数据库会保持单一物理连接，
 * 因此必须通过 {@link #close()} 释放。</p>
 */
public final class SqliteJdbc implements AutoCloseable {
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final int DEFAULT_BUSY_TIMEOUT_MILLIS = 5_000;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SingleConnectionDataSource singleConnectionDataSource;

    private SqliteJdbc(DataSource dataSource, SingleConnectionDataSource singleConnectionDataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        this.singleConnectionDataSource = singleConnectionDataSource;
    }

    public static SqliteJdbc open(Path databaseFile) {
        return open(databaseFile, null);
    }

    public static SqliteJdbc open(Path databaseFile, Consumer<SQLiteConfig> configurer) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        String file = databaseFile.toAbsolutePath().normalize().toString().replace('\\', '/');
        SQLiteDataSource dataSource = createDataSource(JDBC_PREFIX + file, configurer);
        return new SqliteJdbc(dataSource, null);
    }

    /**
     * 创建一个单连接内存数据库。该形式适合测试和单线程工具。
     */
    public static SqliteJdbc inMemory() {
        return inMemory(null);
    }

    public static SqliteJdbc inMemory(Consumer<SQLiteConfig> configurer) {
        SQLiteDataSource source = createDataSource(JDBC_PREFIX + ":memory:", configurer);
        try {
            Connection connection = source.getConnection();
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
            return new SqliteJdbc(dataSource, dataSource);
        } catch (SQLException e) {
            throw new CannotGetJdbcConnectionException("Could not open SQLite in-memory database", e);
        }
    }

    private static SQLiteDataSource createDataSource(String url, Consumer<SQLiteConfig> configurer) {
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setBusyTimeout(DEFAULT_BUSY_TIMEOUT_MILLIS);
        sqliteConfig.enforceForeignKeys(true);
        if (configurer != null) {
            configurer.accept(sqliteConfig);
        }
        SQLiteDataSource dataSource = new SQLiteDataSource(sqliteConfig);
        dataSource.setUrl(url);
        return dataSource;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    public TransactionTemplate transactionTemplate() {
        return transactionTemplate;
    }

    public <T> T transaction(Function<JdbcTemplate, T> action) {
        Objects.requireNonNull(action, "action");
        return transactionTemplate.execute(status -> action.apply(jdbcTemplate));
    }

    public void transactionWithoutResult(Consumer<JdbcTemplate> action) {
        Objects.requireNonNull(action, "action");
        transactionTemplate.executeWithoutResult(status -> action.accept(jdbcTemplate));
    }

    @Override
    public void close() {
        if (singleConnectionDataSource != null) {
            singleConnectionDataSource.close();
        }
    }
}
