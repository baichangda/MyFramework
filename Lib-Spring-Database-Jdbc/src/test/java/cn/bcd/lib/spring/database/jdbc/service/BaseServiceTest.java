package cn.bcd.lib.spring.database.jdbc.service;

import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.NullCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NumberCondition;
import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.SuperBaseBean;
import cn.bcd.lib.spring.database.jdbc.condition.ConvertRes;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaseServiceTest {
    @Table("test_bean")
    public static class Bean extends SuperBaseBean {
        public String name;
    }

    static class Service extends BaseService<Bean> {
    }

    static class RecordingJdbc extends JdbcTemplate {
        final List<String> statements = new ArrayList<>();
        final List<List<Object>> arguments = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            statements.add(sql);
            arguments.add(List.of(args));
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            statements.add(sql);
            arguments.add(List.of(args));
            return (List<T>) List.of(new Bean());
        }
    }

    private final RecordingJdbc jdbc = new RecordingJdbc();
    private final Service service = new Service();

    BaseServiceTest() {
        service.init(jdbc, null);
    }

    @Test
    void missingAndIgnoredWriteConditionsDoNotExecuteSql() {
        Condition[] conditions = {null, NumberCondition.EQUAL("id", null), NumberCondition.NOT_IN("id"), NullCondition.NULL("name")};
        for (Condition condition : conditions) {
            assertDoesNotThrow(() -> service.delete(condition));
            assertDoesNotThrow(() -> service.update(condition, Map.of("name", "new")));
        }
        assertTrue(jdbc.statements.isEmpty());
    }

    @Test
    void validWritesKeepTheirPredicateAndParameters() {
        service.delete(NumberCondition.EQUAL("id", 7));
        service.update(NumberCondition.EQUAL("id", 7), Map.of("name", "new"));
        assertEquals(List.of("delete from test_bean where id=?",
                "update test_bean set name=? where id=?"), jdbc.statements);
        assertEquals(List.of(List.of(7), List.of("new", 7)), jdbc.arguments);
    }

    @Test
    void consecutiveBatchesDoNotAccumulatePaginationParameters() {
        var iterator = service.batchIterable(1, NumberCondition.EQUAL("id", 7), null).iterator();
        iterator.next();
        iterator.next();
        assertEquals(List.of(List.of(7, 1, 0), List.of(7, 1, 1)), jdbc.arguments);
        assertEquals("select * from test_bean where id=? limit ? offset ?", jdbc.statements.getFirst());
    }

    @Test
    void paginationAcceptsImmutableConditionParameters() {
        ConvertRes condition = new ConvertRes("id=?", List.of(7));
        service.list(condition, null, 20, 10);
        assertEquals(List.of(7), condition.paramList);
        assertEquals(List.of(7, 10, 20), jdbc.arguments.getFirst());
        assertEquals("select * from test_bean where id=? limit ? offset ?", jdbc.statements.getFirst());
    }

    @Test
    void mapInsertUsesPortableInsertIntoSyntax() {
        service.insert(Map.of("name", "value"));
        assertEquals("insert into test_bean(name) values(?)", jdbc.statements.getFirst());
        assertEquals(List.of("value"), jdbc.arguments.getFirst());
    }
}
