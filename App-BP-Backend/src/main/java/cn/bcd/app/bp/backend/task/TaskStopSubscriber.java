package cn.bcd.app.bp.backend.task;

import cn.bcd.lib.spring.redis.mq.RedisTopicMQ;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/** 只传递停止通知；任务数据和停止意图均保存在 MySQL，不在 Redis 中排队或等待应答。 */
@Component
public class TaskStopSubscriber extends RedisTopicMQ<Long> {
    private final TaskWorker worker;

    public TaskStopSubscriber(RedisConnectionFactory factory, TaskProperties properties, TaskWorker worker) {
        super(factory, 1, 1, Long.class, properties.getStopTopic());
        this.worker = worker;
    }

    @PostConstruct
    void subscribe() {
        init();
    }

    @PreDestroy
    void unsubscribe() {
        close();
    }

    @Override
    public void onMessage(Long id) {
        worker.requestStop(id);
    }
}
