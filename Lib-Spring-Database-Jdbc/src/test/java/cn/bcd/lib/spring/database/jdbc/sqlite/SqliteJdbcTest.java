package cn.bcd.lib.spring.database.jdbc.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.DataClassRowMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteJdbcTest {
    record User(long id, String userName) {
    }

    @Test
    void fileDatabaseSupportsCrud(@TempDir Path tempDir) {
        try (SqliteJdbc sqlite = SqliteJdbc.open(tempDir.resolve("crud.db"))) {
            var jdbc = sqlite.jdbcTemplate();
            jdbc.execute("create table user(id integer primary key autoincrement, user_name text not null)");
            jdbc.update("insert into user(user_name) values (?)", "Alice");

            List<User> users = jdbc.query(
                    "select id, user_name from user",
                    DataClassRowMapper.newInstance(User.class));
            assertEquals(List.of(new User(1L, "Alice")), users);

            jdbc.update("update user set user_name=? where id=?", "Bob", 1L);
            assertEquals("Bob", jdbc.queryForObject("select user_name from user where id=?", String.class, 1L));

            jdbc.update("delete from user where id=?", 1L);
            assertEquals(0, jdbc.queryForObject("select count(*) from user", Integer.class));
        }
    }

    @Test
    void transactionRollsBack() {
        try (SqliteJdbc sqlite = SqliteJdbc.inMemory()) {
            var jdbc = sqlite.jdbcTemplate();
            jdbc.execute("create table user(id integer primary key autoincrement, user_name text not null)");

            assertThrows(IllegalStateException.class, () -> sqlite.transactionWithoutResult(transactionJdbc -> {
                transactionJdbc.update("insert into user(user_name) values (?)", "Alice");
                throw new IllegalStateException("rollback");
            }));

            assertEquals(0, jdbc.queryForObject("select count(*) from user", Integer.class));
        }
    }

    @Test
    void foreignKeysAreEnabledByDefault() {
        try (SqliteJdbc sqlite = SqliteJdbc.inMemory()) {
            var jdbc = sqlite.jdbcTemplate();
            jdbc.execute("create table parent(id integer primary key)");
            jdbc.execute("create table child(parent_id integer references parent(id))");

            assertThrows(DataAccessException.class,
                    () -> jdbc.update("insert into child(parent_id) values (?)", 1L));
        }
    }

    @Test
    void acceptsNativeSQLiteConfiguration() {
        try (SqliteJdbc sqlite = SqliteJdbc.inMemory(config -> config.setBusyTimeout(1_234))) {
            assertEquals(1_234,
                    sqlite.jdbcTemplate().queryForObject("pragma busy_timeout", Integer.class));
        }
    }
}
