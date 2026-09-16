package cn.bcd.lib.spring.database.jdbc.conn;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConnTest {
    public record User(long id, String userName) {
    }

    public static class Parent {
        public long id;
    }

    public static class Bean extends Parent {
        public String userName;
    }

    @Test
    void insertAndUpdateContainEachFieldOnce() {
        var insert = Conn.toInsertSqlResult(User.class, "users", true, null);
        assertEquals("insert into users(id,user_name)", insert.sqlPrefix());
        assertEquals("(?,?)", insert.sqlSuffix());
        assertEquals(2, insert.insertFields().length);
        var update = Conn.toUpdateSqlResult(User.class, "users", true,
                field -> !field.getName().equals("id"), "id");
        assertEquals("update users set user_name=? where id=?", update.sql());
        assertEquals(1, update.updateFields().length);
    }

    @Test
    void inheritedBeanFieldsAndUnconvertedNamesAreNotDuplicated() {
        var insert = Conn.toInsertSqlResult(Bean.class, "users", false, null);
        assertEquals("insert into users(userName,id)", insert.sqlPrefix());
        assertEquals(2, insert.insertFields().length);
    }

    @Test
    void readsCamelCaseRecordUsingConstructorComponentCount() throws Exception {
        Driver driver = mock(Driver.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        String url = "jdbc:conn-test:record";
        when(driver.connect(eq(url), any(Properties.class))).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnName(1)).thenReturn("id");
        when(metadata.getColumnName(2)).thenReturn("user_name");
        when(rows.next()).thenReturn(true, false);
        when(rows.getObject(1)).thenReturn(7L);
        when(rows.getObject(2)).thenReturn("Alice");
        DriverManager.registerDriver(driver);
        try {
            Conn conn = new Conn(url);
            assertEquals(List.of(new User(7L, "Alice")), conn.list("select * from users", User.class));
        } finally {
            DriverManager.deregisterDriver(driver);
            connection.close();
        }
    }
}
