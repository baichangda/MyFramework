package cn.bcd.lib.spring.database.jdbc.service;

import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.NullCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NumberCondition;
import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.SuperBaseBean;
import cn.bcd.lib.spring.database.jdbc.condition.ConvertRes;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
        public <T> T queryForObject(String sql, Class<T> type) {
            statements.add(sql);
            return type.cast(1);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper) {
            return query(sql, mapper, new Object[0]);
        }

        @Override
        public int[] batchUpdate(String sql, List<? extends Object[]> batchArgs) {
            statements.add(sql);
            for (Object[] args : batchArgs) {
                arguments.add(java.util.Arrays.asList(args));
            }
            return new int[batchArgs.size()];
        }

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

    static class DerivedService extends Service {
    }

    static class GenericService<T extends SuperBaseBean> extends BaseService<T> {
    }

    static class ConcreteService extends GenericService<Bean> {
    }

    @Test
    void resolvesEntityThroughInheritance() {
        assertEquals(Bean.class, new DerivedService().getBeanInfo().clazz);
        assertEquals(Bean.class, new ConcreteService().getBeanInfo().clazz);
    }

    @Test
    void nullPredicatesArePreservedForReadsAndWrites() {
        service.list(NullCondition.NULL("name"));
        service.count(NullCondition.NOT_NULL("name"));
        service.delete(NullCondition.NULL("name"));
        service.update(NullCondition.NOT_NULL("name"), Map.of("name", "new"));
        assertEquals(List.of("select * from test_bean where name is null",
                "select count(*) from test_bean where name is not null",
                "delete from test_bean where name is null",
                "update test_bean set name=? where name is not null"), jdbc.statements);
    }

    @Test
    void mixedIdsAreInsertedInSeparateBatchesRegardlessOfInputOrder() {
        Bean generated = new Bean();
        generated.name = "generated";
        Bean assigned = new Bean();
        assigned.setId(7L);
        assigned.name = "assigned";
        Bean generated2 = new Bean();
        generated2.name = "generated2";
        Bean assigned2 = new Bean();
        assigned2.setId(8L);
        assigned2.name = "assigned2";
        for (List<Bean> input : List.of(List.of(generated, assigned, generated2, assigned2),
                List.of(assigned, generated, assigned2, generated2))) {
            jdbc.statements.clear();
            jdbc.arguments.clear();
            service.insertBatch(input);
            assertEquals(List.of(service.getBeanInfo().insertSql, service.getBeanInfo().insertSql_noId), jdbc.statements);
            assertEquals(List.of(List.of(7L, "assigned"), List.of(8L, "assigned2"),
                    List.of("generated"), List.of("generated2")), jdbc.arguments);
            assertNull(generated.getId());
            assertNull(generated2.getId());
        }
    }

    @Test
    void homogeneousAndEmptyBatchesSkipEmptyGroups() {
        Bean generated = new Bean();
        generated.name = "generated";
        Bean assigned = new Bean();
        assigned.setId(7L);
        assigned.name = "assigned";
        service.insertBatch(List.of());
        assertTrue(jdbc.statements.isEmpty());
        service.insertBatch(List.of(generated));
        service.insertBatch(List.of(assigned));
        assertEquals(List.of(service.getBeanInfo().insertSql_noId, service.getBeanInfo().insertSql), jdbc.statements);
        assertEquals(List.of(List.of("generated"), List.of(7L, "assigned")), jdbc.arguments);
    }

    @Test
    void largePageDoesNotOverflowAndQueryEarlierRows() {
        assertTrue(service.page(PageRequest.of(Integer.MAX_VALUE, 2)).isEmpty());
        assertEquals(List.of("select count(*) from test_bean"), jdbc.statements);
    }

    @Test
    void unpagedRequestReturnsContent() {
        assertEquals(1, service.page(Pageable.unpaged()).getNumberOfElements());
        assertEquals("select * from test_bean", jdbc.statements.getLast());
    }

    @Test
    void singleResultQueryLimitsDatabaseRows() {
        assertNotNull(service.get(NumberCondition.EQUAL("id", 7)));
        assertEquals("select * from test_bean where id=? limit ? offset ?", jdbc.statements.getFirst());
        assertEquals(List.of(7, 1, 0L), jdbc.arguments.getFirst());
    }

    private final RecordingJdbc jdbc = new RecordingJdbc();
    private final Service service = new Service();

    BaseServiceTest() {
        service.init(jdbc, null);
    }

    @Test
    void missingAndIgnoredWriteConditionsDoNotExecuteSql() {
        Condition[] conditions = {null, NumberCondition.EQUAL("id", null), NumberCondition.NOT_IN("id")};
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
        assertEquals(List.of(List.of(7, 1, 0L), List.of(7, 1, 1L)), jdbc.arguments);
        assertEquals("select * from test_bean where id=? limit ? offset ?", jdbc.statements.getFirst());
    }

    @Test
    void paginationAcceptsImmutableConditionParameters() {
        ConvertRes condition = new ConvertRes("id=?", List.of(7));
        service.list(condition, null, 20, 10);
        assertEquals(List.of(7), condition.paramList);
        assertEquals(List.of(7, 10, 20L), jdbc.arguments.getFirst());
        assertEquals("select * from test_bean where id=? limit ? offset ?", jdbc.statements.getFirst());
    }

    @Test
    void mapInsertUsesPortableInsertIntoSyntax() {
        service.insert(Map.of("name", "value"));
        assertEquals("insert into test_bean(name) values(?)", jdbc.statements.getFirst());
        assertEquals(List.of("value"), jdbc.arguments.getFirst());
    }
}
