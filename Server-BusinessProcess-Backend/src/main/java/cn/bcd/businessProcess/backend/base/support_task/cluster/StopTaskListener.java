package cn.bcd.businessProcess.backend.base.support_task.cluster;

import cn.bcd.base.redis.mq.ValueSerializerType;
import cn.bcd.base.redis.mq.topic.RedisTopicMQ;
import cn.bcd.businessProcess.backend.base.support_task.Task;
import cn.bcd.businessProcess.backend.base.support_task.TaskRunnable;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.io.Serializable;

public class StopTaskListener<T extends Task<K>, K extends Serializable> extends RedisTopicMQ<StopRequest> {

    final ClusterTaskBuilder<T, K> taskBuilder;

    StopTaskResultListener<T, K> stopTaskResultListener;

    public StopTaskListener(String name, RedisConnectionFactory connectionFactory, ClusterTaskBuilder<T, K> taskBuilder) {
        super(connectionFactory, 1, 1, ValueSerializerType.JACKSON, "stopTask:" + name);
        this.taskBuilder = taskBuilder;
    }

    @Override
    public void init() {
        this.stopTaskResultListener = taskBuilder.getStopTaskResultListener();
        super.init();
    }

    @Override
    public void onMessage(StopRequest stopRequest) {
        //依次停止每个任务,将结束的任务记录到结果map中
        StopResultRequest stopResultRequest = new StopResultRequest(stopRequest.requestId);
        final String[] ids = stopRequest.ids;
        for (String id : ids) {
            TaskRunnable<T, K> runnable = taskBuilder.getTaskIdToRunnable().get(id);
            if (runnable != null) {
                stopResultRequest.resMap.put(id, runnable.stop().flag + "");
            }
        }
        stopTaskResultListener.send(stopResultRequest);
    }

}
