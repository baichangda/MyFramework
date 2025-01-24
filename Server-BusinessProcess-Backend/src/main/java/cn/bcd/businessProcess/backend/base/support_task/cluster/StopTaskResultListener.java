package cn.bcd.businessProcess.backend.base.support_task.cluster;

import cn.bcd.businessProcess.backend.base.support_redis.mq.ValueSerializerType;
import cn.bcd.businessProcess.backend.base.support_redis.mq.topic.RedisTopicMQ;
import cn.bcd.businessProcess.backend.base.support_task.StopResult;
import cn.bcd.businessProcess.backend.base.support_task.Task;
import com.google.common.collect.Maps;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

public class StopTaskResultListener<T extends Task<K>, K extends Serializable> extends RedisTopicMQ<StopResultRequest> {

    private final ClusterTaskBuilder<T, K> taskBuilder;

    public StopTaskResultListener(String name, RedisConnectionFactory connectionFactory, ClusterTaskBuilder<T, K> taskBuilder) {
        super(connectionFactory, 1, 1, ValueSerializerType.JACKSON, "stopTaskResult:" + name);
        this.taskBuilder = taskBuilder;
    }

    @Override
    public void onMessage(StopResultRequest stopRequest) {
        final String requestId = stopRequest.requestId;
        Optional.ofNullable(taskBuilder.getRequestIdToResultMap().get(requestId)).ifPresent(e -> {
            final Map<String, String> filterMap = Maps.filterValues(stopRequest.resMap,
                    v -> Integer.parseInt(v) != StopResult.WAIT_OR_IN_EXECUTING_NOT_FOUND.flag);
            synchronized (e) {
                stopRequest.resMap.forEach((k, v) -> {
                    if (StopResult.from(Integer.parseInt(v)) != StopResult.WAIT_OR_IN_EXECUTING_NOT_FOUND) {
                        e.putAll(filterMap);
                    }
                });
                e.notify();
            }
        });
    }

    public ClusterTaskBuilder<T, K> getTaskBuilder() {
        return taskBuilder;
    }
}
