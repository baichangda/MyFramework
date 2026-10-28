package cn.bcd.app.bp.backend.task;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class TaskStopSubscriberTest {
    @Test
    void broadcastsStopToBothNodesUsingConfiguredRedis() {
        var factory = TaskTestConnections.redis();
        TaskProperties properties = new TaskProperties();
        // 隔离 topic，不向运行中的业务节点发送测试停止指令。
        properties.setStopTopic("task-test-stop-" + UUID.randomUUID());
        TaskWorker firstWorker = mock(TaskWorker.class);
        TaskWorker secondWorker = mock(TaskWorker.class);
        try (TaskStopSubscriber first = new TaskStopSubscriber(factory, properties, firstWorker);
             TaskStopSubscriber second = new TaskStopSubscriber(factory, properties, secondWorker)) {
            first.subscribe();
            second.subscribe();
            first.send(123L);
            verify(firstWorker, timeout(10000)).requestStop(123L);
            verify(secondWorker, timeout(10000)).requestStop(123L);
        } finally {
            factory.destroy();
            factory.getClientConfiguration().getClientResources().ifPresent(resources -> resources.shutdown());
        }
    }
}
