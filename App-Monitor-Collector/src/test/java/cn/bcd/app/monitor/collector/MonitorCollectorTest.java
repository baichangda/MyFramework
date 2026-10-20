package cn.bcd.app.monitor.collector;

import cn.bcd.Application;
import cn.bcd.lib.spring.monitor.client.MonitorData;
import cn.bcd.lib.spring.monitor.client.MonitorProp;
import cn.bcd.lib.spring.monitor.client.MonitorRedisTopicMQ;
import cn.bcd.lib.spring.monitor.client.SystemData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MonitorCollectorTest {
    @Test
    void ignoresInvalidAndOtherBatchResponsesAndKeepsNewestDuplicate() {
        ServerData a = server("a");
        ServerData b = server("b");
        MonitorData newest = response("a", 10);
        newest.systemData.cpuUsePercent = 25;
        MonitorData invalid = response(null, 10);
        List<SaveData> rows = MonitorCollector.buildSaveData(List.of(a, b), 10,
                Arrays.asList(null, invalid, response("b", 9), newest, response("a", 10)));
        assertEquals(2, rows.size());
        assertEquals(0, rows.getFirst().status);
        assertTrue(rows.getFirst().systemData.contains("25"));
        assertEquals(1, rows.get(1).status);
        assertEquals(10, rows.get(1).batch);
    }

    @Test
    void emptyResponsesMarkAllRegisteredServersOffline() {
        assertEquals(1, MonitorCollector.buildSaveData(List.of(server("a")), 10, null).getFirst().status);
    }

    @Test
    void collectsAndInsertsCurrentBatchWithoutReplayingPreviousResults() {
        try (Harness h = new Harness()) {
            h.collector.collect();
            assertEquals(1, h.mq.sent);
            assertEquals(1, h.responses.deletes);
            assertEquals(2, h.jdbc.rows.size());
            Object[] online = h.jdbc.rows.getFirst();
            assertEquals("a", online[0]);
            assertEquals(0, online[3]);
            assertNotNull(online[4]);
            Object[] offline = h.jdbc.rows.get(1);
            assertEquals("b", offline[0]);
            assertEquals(1, offline[3]);
            assertNull(offline[4]);
            h.collector.collect();
            assertEquals(2, h.mq.sent);
            assertEquals(2, h.responses.deletes);
            assertEquals(4, h.jdbc.rows.size());
        }
    }

    @Test
    void interruptionRestoresFlagAndDoesNotSaveFalseOfflineResults() {
        try (Harness h = new Harness()) {
            h.collector.interrupted = true;
            try {
                h.collector.collect();
                assertTrue(Thread.currentThread().isInterrupted());
                assertTrue(h.jdbc.rows.isEmpty());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void actualApplicationConfigurationRegistersScheduledCollector() {
        assertTrue(Application.class.isAnnotationPresent(EnableScheduling.class));
        try (Harness h = new Harness();
             AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource("application.yml"));
            PropertySourcesPlaceholderConfigurer placeholders = new PropertySourcesPlaceholderConfigurer();
            placeholders.setProperties(Objects.requireNonNull(yaml.getObject()));
            context.addBeanFactoryPostProcessor(placeholders);
            context.register(Scheduling.class);
            context.registerBean(MonitorProp.class, () -> h.prop);
            context.registerBean(MonitorRedisTopicMQ.class, () -> h.mq);
            context.registerBean(JdbcTemplate.class, () -> h.jdbc);
            context.registerBean(MonitorCollector.class, () -> h.collector);
            context.refresh();
            assertEquals(1, context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().size());
        }
    }

    @Configuration
    @EnableScheduling
    static class Scheduling {
    }

    static ServerData server(String id) {
        ServerData server = new ServerData();
        server.serverId = id;
        server.serverType = 2;
        return server;
    }

    static MonitorData response(String id, long batch) {
        MonitorData data = new MonitorData();
        data.serverId = id;
        data.batch = batch;
        data.systemData = new SystemData();
        return data;
    }

    static RedisConnectionFactory factory() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
    }

    static class Harness implements AutoCloseable {
        final MonitorProp prop = new MonitorProp();
        final FakeJdbc jdbc = new FakeJdbc();
        final FakeResponses responses = new FakeResponses();
        final FakeMQ mq = new FakeMQ(prop, responses);
        TestCollector collector = newCollector();

        TestCollector newCollector() {
            TestCollector result = new TestCollector();
            result.monitorProp = prop;
            result.monitorRedisTopicMQ = mq;
            result.jdbcTemplate = jdbc;
            result.redisTemplate = responses;
            return result;
        }

        @Override
        public void close() {
            mq.close();
        }
    }

    static class TestCollector extends MonitorCollector {
        boolean interrupted;
        TestCollector() {
            super(factory());
        }
        @Override
        protected void awaitResponses() throws InterruptedException {
            if (interrupted) {
                throw new InterruptedException();
            }
        }
    }

    static class FakeMQ extends MonitorRedisTopicMQ {
        int sent;
        final FakeResponses responses;
        FakeMQ(MonitorProp prop, FakeResponses responses) {
            super(factory(), prop);
            this.responses = responses;
        }
        @Override
        public void send(String data, String... names) {
            sent++;
            responses.batch = Long.parseLong(data);
        }
    }

    static class FakeResponses extends RedisTemplate<String, MonitorData> {
        long batch;
        int deletes;
        @Override
        @SuppressWarnings("unchecked")
        public ListOperations<String, MonitorData> opsForList() {
            return (ListOperations<String, MonitorData>) Proxy.newProxyInstance(ListOperations.class.getClassLoader(),
                    new Class<?>[]{ListOperations.class}, (proxy, method, args) -> {
                        assertEquals("range", method.getName());
                        assertEquals("monitorResponse:" + batch, args[0]);
                        return List.of(response("a", batch), response("b", batch - 1));
                    });
        }
        @Override
        public Boolean delete(String key) {
            assertEquals("monitorResponse:" + batch, key);
            deletes++;
            return true;
        }
    }

    static class FakeJdbc extends JdbcTemplate {
        @Override
        public void afterPropertiesSet() {
        }
        List<Object[]> rows = new ArrayList<>();
        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> mapper) {
            return (List<T>) List.of(server("a"), server("b"));
        }
        @Override
        public int[] batchUpdate(String sql, List<? extends Object[]> args) {
            rows.addAll(args);
            return new int[args.size()];
        }
    }

}
