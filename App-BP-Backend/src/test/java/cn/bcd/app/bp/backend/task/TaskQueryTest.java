package cn.bcd.app.bp.backend.task;

import cn.bcd.app.bp.backend.file.FileService;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.jdbc.condition.ConditionUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskQueryTest {
    @Test
    void pageMapsTaskSnapshotAndBuildsBusinessFilters() {
        TaskService service = spy(new TaskService(mock(TaskWorker.class), mock(TaskStopSubscriber.class), mock(FileService.class)));
        TaskBean bean = new TaskBean("export", "report");
        bean.id = 7L;
        bean.stopRequested = true;
        bean.createTime = new Date(1234);
        doReturn(new PageImpl<>(List.of(bean), PageRequest.of(1, 5), 6))
                .when(service).page(any(Condition.class), eq(PageRequest.of(1, 5)));
        TaskQueryRequest query = new TaskQueryRequest();
        query.status = 2;
        query.startTimeBegin = new Date(100);

        var queryResponsePage = service.pageTasks(query, 2, 5);
        assertEquals(6, queryResponsePage.getTotalElements());
        assertEquals(7L, queryResponsePage.getContent().getFirst().id);
        assertTrue(queryResponsePage.getContent().getFirst().stopRequested);
        assertEquals(bean.createTime, queryResponsePage.getContent().getFirst().createTime);
        verify(service).page(argThat((Condition condition) -> {
            var converted = ConditionUtil.convertCondition(condition, service.getBeanInfo());
            return converted.sql.contains("status=?") && converted.sql.contains("start_time>=?")
                    && !converted.sql.contains("create_time");
        }), eq(PageRequest.of(1, 5)));
    }
}
