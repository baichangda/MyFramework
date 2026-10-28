package cn.bcd.app.bp.backend.task;

import cn.bcd.app.bp.backend.file.FileService;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;
import cn.bcd.lib.spring.database.jdbc.service.BaseService;
import cn.bcd.lib.spring.database.jdbc.service.BatchIterable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportHandlerTest {
    private final TaskService dataService = mock(TaskService.class);
    private final FileService fileService = mock(FileService.class);

    @Test
    @SuppressWarnings("unchecked")
    void workerBuildsQueryFromParametersThenExportsUploadsAndCleansUp() throws Exception {
        BatchIterable<TaskBean> batches = mock(BatchIterable.class);
        when(dataService.batchIterable(eq(1000), any(), any())).thenReturn(batches);
        TaskBean row = new TaskBean("Alice", "user-export");
        row.id = 10L;
        when(batches.iterator()).thenReturn(List.of(List.of(row)).iterator());
        AtomicReference<Path> uploaded = new AtomicReference<>();
        doAnswer(invocation -> {
            Path path = invocation.getArgument(1);
            assertTrue(Files.size(path) > 0);
            uploaded.set(path);
            return null;
        }).when(fileService).upload(anyString(), any(Path.class));

        TaskBean task = task();
        // 数据库中只有查询参数，Service、Condition 和查询结果由执行节点生成。
        task.payload = JsonUtil.toJson(new Query("Alice"));
        TaskService persistence = mock(TaskService.class);
        new TaskWorker(persistence, new TaskProperties(), List.of(handler())).execute(task);

        assertEquals(TaskStatus.SUCCEED.getStatus(), task.status);
        assertEquals("export/users_100.xlsx", task.result);
        ArgumentCaptor<Condition> condition = ArgumentCaptor.forClass(Condition.class);
        verify(dataService).batchIterable(eq(1000), condition.capture(), eq(Sort.by("id")));
        StringCondition filter = assertInstanceOf(StringCondition.class, condition.getValue());
        assertEquals("name", filter.fieldName);
        assertEquals("Alice", filter.val);
        verify(fileService).upload(eq(task.result), any(Path.class));
        assertNotNull(uploaded.get());
        assertFalse(Files.exists(uploaded.get()));
        verify(persistence).finish(task);
    }

    @Test
    void stoppedExportDoesNotQueryOrUpload() {
        TaskContext context = new TaskContext(task(), mock(TaskService.class));
        context.requestStop();
        assertThrows(TaskStopException.class, () -> handler().execute(context, new Query("Alice")));
        verifyNoInteractions(dataService, fileService);
    }

    private ExportHandler<Query> handler() {
        return new ExportHandler<>(fileService) {
            public String type() { return "user-export"; }
            public Class<Query> payloadType() { return Query.class; }
            protected BaseService<?> service() { return dataService; }
            protected Condition condition(Query query) { return StringCondition.EQUAL("name", query.name()); }
            protected Sort sort(Query query) { return Sort.by("id"); }
        };
    }

    private TaskBean task() {
        TaskBean task = new TaskBean("users", "user-export");
        task.id = 100L;
        task.status = TaskStatus.EXECUTING.getStatus();
        return task;
    }

    public record Query(String name) {}
}
