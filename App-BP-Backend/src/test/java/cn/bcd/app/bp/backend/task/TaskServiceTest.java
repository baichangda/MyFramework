package cn.bcd.app.bp.backend.task;

import cn.bcd.app.bp.backend.file.FileService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskServiceTest {
    private final TaskWorker worker = mock(TaskWorker.class);
    private final TaskStopSubscriber subscriber = mock(TaskStopSubscriber.class);
    private final FileService fileService = mock(FileService.class);
    private final TaskService service = spy(new TaskService(worker, subscriber, fileService));

    @Test
    void deletesExportFilesForCustomTaskTypesWithoutTreatingOtherResultsAsFiles() {
        TaskBean exported = new TaskBean("users", "user-export");
        exported.result = "export/users.xlsx";
        TaskBean report = new TaskBean("report", "report");
        report.result = "completed";
        doReturn(java.util.List.of(exported, report)).when(service).deleteFinished(1L, 2L);
        service.deleteTaskAndDeleteFile(1L, 2L);
        verify(fileService).delete("export/users.xlsx");
        verifyNoMoreInteractions(fileService);
    }

    @Test
    void waitingCancellationIsPersistedBeforeCallbackAndDoesNotBroadcast() {
        TaskBean task = new TaskBean("test", "export");
        task.id = 1L;
        task.status = 1;
        doReturn(task).when(service).get(1);
        doReturn(true).when(service).cancelWaiting(1);

        assertArrayEquals(new StopResult[]{StopResult.CANCEL_SUCCEED}, service.stop(1L));

        var order = inOrder(service, worker);
        order.verify(service).cancelWaiting(1);
        order.verify(worker).canceled(task);
        assertEquals(5, task.status);
        assertNotNull(task.finishTime);
        verifyNoInteractions(subscriber);
        verify(service, never()).requestStop(anyLong());
    }

    @Test
    void losingCancellationRaceRequestsStopBeforeBroadcastWithoutWritingTerminalState() {
        TaskBean task = new TaskBean("test", "export");
        task.id = 1L;
        task.status = 1; // 查询后被其他节点领取，取消条件更新失败。
        doReturn(task).when(service).get(1);
        doReturn(false).when(service).cancelWaiting(1);
        doReturn(true).when(service).requestStop(1);

        assertArrayEquals(new StopResult[]{StopResult.STOP_REQUESTED}, service.stop(1L));

        var order = inOrder(service, worker, subscriber);
        order.verify(service).cancelWaiting(1);
        order.verify(service).requestStop(1);
        order.verify(worker).requestStop(1);
        order.verify(subscriber).send(1L);
        verify(worker, never()).canceled(any());
        verify(service, never()).finish(any());
    }

    @Test
    void completedAndMissingTasksAreNotBroadcast() {
        TaskBean completed = new TaskBean("test", "export");
        completed.id = 1L;
        completed.status = 3;
        doReturn(completed).when(service).get(1);
        doReturn(null).when(service).get(2);

        doReturn(false).when(service).cancelWaiting(1);
        doReturn(false).when(service).requestStop(1);
        assertArrayEquals(new StopResult[]{StopResult.WAIT_OR_IN_EXECUTING_NOT_FOUND,
                StopResult.WAIT_OR_IN_EXECUTING_NOT_FOUND}, service.stop(1L, 2L));
        verifyNoInteractions(worker, subscriber);
    }
}
