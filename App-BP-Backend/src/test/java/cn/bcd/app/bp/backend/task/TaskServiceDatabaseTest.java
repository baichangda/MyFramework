package cn.bcd.app.bp.backend.task;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 使用 application.yml 的真实 MySQL；独立临时库隔离测试，不接触业务任务表。 */
class TaskServiceDatabaseTest {
    private static final String DATABASE = "task_test_" + UUID.randomUUID().toString().replace("-", "");
    private static HikariDataSource admin;
    private static HikariDataSource dataSource;
    private static boolean databaseCreated;
    private TaskService service;

    @BeforeAll
    static void createDatabase() {
        admin = TaskTestConnections.mysql(null);
        new JdbcTemplate(admin).execute("create database `" + DATABASE + "`");
        databaseCreated = true;
        dataSource = TaskTestConnections.mysql(DATABASE);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                create table t_sys_task (
                    id bigint primary key, name varchar(50), type varchar(64), payload longtext,
                    status int, percent float default 0, result varchar(255), message varchar(255),
                    start_time timestamp, finish_time timestamp, create_time timestamp,
                    create_user_id bigint, create_user_name varchar(50),
                    update_time timestamp, update_user_id bigint, update_user_name varchar(50),
                    node_id varchar(128), stop_requested boolean default false
                ) engine=InnoDB
                """);
    }

    @AfterAll
    static void dropDatabase() {
        try {
            if (dataSource != null) {
                dataSource.close();
            }
            // 仅允许删除本测试本次生成的临时库。
            if (databaseCreated && DATABASE.matches("task_test_[0-9a-f]{32}")) {
                new JdbcTemplate(admin).execute("drop database `" + DATABASE + "`");
            }
        } finally {
            if (admin != null) {
                admin.close();
            }
        }
    }

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from t_sys_task");
        service = new TaskService(org.mockito.Mockito.mock(TaskWorker.class), org.mockito.Mockito.mock(TaskStopSubscriber.class),
                org.mockito.Mockito.mock(cn.bcd.app.bp.backend.file.FileService.class));
        service.init(jdbc, new TransactionTemplate());
        jdbc.update("insert into t_sys_task(id, name, type, payload, status) values(1,'test','export','{}',1)");
    }

    @Test
    void insertFillsCreationMetadataFromBaseService() {
        var user = org.mockito.Mockito.mock(cn.bcd.lib.spring.database.jdbc.bean.UserInterface.class);
        org.mockito.Mockito.when(user.getId()).thenReturn(7L);
        org.mockito.Mockito.when(user.getUsername()).thenReturn("creator");
        var getter = new cn.bcd.lib.spring.database.jdbc.service.UserGetter();
        getter.setConsumer(() -> user);
        try {
            TaskBean task = new TaskBean("metadata", "export");
            task.id = 2L;
            task.status = TaskStatus.WAITING.getStatus();
            task.payload = "{}";
            service.insert(task);
            TaskBean saved = service.get(2);
            assertNotNull(saved.createTime);
            assertEquals(7L, saved.createUserId);
            assertEquals("creator", saved.createUserName);
        } finally {
            getter.setConsumer(null);
        }
    }

    @Test
    void onlyOneNodeCanClaimTheSameTask() throws Exception {
        List<Boolean> results = race(() -> service.claim(1, "a"), () -> service.claim(1, "b"));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(2, service.get(1).status);
        assertNotNull(service.get(1).startTime);
        assertNull(service.findWaiting(List.of("export")));
    }

    @Test
    void claimAndCancellationHaveExactlyOneWinner() throws Exception {
        List<Boolean> results = race(() -> service.claim(1, "node"), () -> service.cancelWaiting(1));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertTrue(List.of(2, 5).contains(service.get(1).status));
        assertNull(service.findWaiting(List.of("export")));
    }

    @Test
    void progressAndCompletionDoNotOverwriteStopIntentOrTerminalState() {
        assertTrue(service.claim(1, "node"));
        TaskBean stale = service.get(1);
        assertFalse(stale.stopRequested);
        assertTrue(service.requestStop(1));
        assertTrue(service.requestStop(1));

        stale.percent = 50;
        service.saveProgress(stale);
        stale.status = TaskStatus.STOPPED.getStatus();
        stale.finishTime = new Date();
        service.finish(stale);

        assertTrue(service.get(1).stopRequested);
        assertEquals(6, service.get(1).status);
        stale.status = TaskStatus.SUCCEED.getStatus();
        service.finish(stale);
        assertEquals(6, service.get(1).status);
        assertFalse(service.requestStop(1));
    }

    @Test
    void filtersTypesAndKeepsActiveRecordsWhenDeleting() {
        assertNull(service.findWaiting(List.of("report")));
        assertEquals(1L, service.findWaiting(List.of("export")));
        assertTrue(service.deleteFinished(1).isEmpty());
        assertTrue(service.cancelWaiting(1));
        assertEquals(1, service.deleteFinished(1).size());
        assertNull(service.get(1));
    }

    private List<Boolean> race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { start.await(); return first.call(); });
            var b = executor.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }
}
