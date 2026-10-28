package cn.bcd.app.bp.backend.task;

import cn.bcd.lib.spring.redis.mq.RedisTopicMQ;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * 集群停止通知通道，消息体只有任务 ID。任务数据和停止意图均保存在 MySQL。
 * 广播不等待节点应答，也不提供补发；收到通知的节点只尝试标记自己持有的任务。
 */
@Component
public class TaskStopSubscriber extends RedisTopicMQ<Long> {
    private final TaskWorker worker;

    public TaskStopSubscriber(RedisConnectionFactory factory, TaskProperties properties, TaskWorker worker) {
        super(factory, 1, 1, Long.class, properties.getStopTopic());
        this.worker = worker;
    }

    /** Bean 初始化时建立订阅，在 Spring 生命周期启动 Worker 之前准备好通知通道。 */
    @PostConstruct
    void subscribe() {
        init();
    }

    /** Spring 先停止并等待 Worker 收尾，再销毁本通知通道。 */
    @PreDestroy
    void unsubscribe() {
        close();
    }

    /** Redis 消费线程不执行生命周期回调、不写任务状态，只设置可见的停止标记。 */
    @Override
    public void onMessage(Long id) {
        worker.requestStop(id);
    }
}
