package cn.bcd.app.businessProcess.backend.base.support_task;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.redis.mq.ValueSerializerType;
import cn.bcd.lib.spring.redis.mq.topic.RedisTopicMQ;
import cn.bcd.lib.base.util.DateZoneUtil;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BroadcastStopper extends RedisTopicMQ<BroadcastStopper.StopCmd> {

    static String topic = "task-stop-broadcast";
    static String queuePre = "task-stop:";

    TaskBuilder<?, ?> taskBuilder;
    final long waitInSecond;

    public BroadcastStopper(RedisConnectionFactory connectionFactory, int waitInSecond) {
        super(connectionFactory, 1, 1, ValueSerializerType.JACKSON, topic);
        this.waitInSecond = waitInSecond;
    }

    @Override
    public void onMessage(StopCmd stopCmd) {
        StopResult[] stopResults = taskBuilder.stopInternal(stopCmd.ids);
        byte[] jsonAsBytes = JsonUtil.toJsonAsBytes(stopResults);
        redisTemplate.boundListOps(queuePre + stopCmd.cmdId).leftPush(jsonAsBytes);
    }

    public record StopCmd(String cmdId, String[] ids) {

    }

    public final StopResult[] stop(String... ids) {
        String cmdId = DateZoneUtil.dateToStr_yyyyMMddHHmmssSSS(new Date());
        StopCmd stopCmd = new StopCmd(cmdId, ids);
        //发送
        send(stopCmd);
        //等待结果
        try {
            TimeUnit.SECONDS.sleep(waitInSecond);
        } catch (InterruptedException e) {
            throw BaseException.get(e);
        }

        //获取结果
        List<StopResult[]> list = new ArrayList<>();
        try {
            List<byte[]> range = redisTemplate.boundListOps(queuePre + cmdId).range(0, -1);
            if (range != null) {
                for (byte[] bytes : range) {
                    StopResult[] stopResults = JsonUtil.OBJECT_MAPPER.readValue(bytes, StopResult[].class);
                    list.add(stopResults);
                }
                //删除缓存
                redisTemplate.delete(cmdId);
            }
        } catch (IOException e) {
            throw BaseException.get(e);
        }

        //合并结果
        StopResult[] result = new StopResult[ids.length];
        for (int i = 0; i < ids.length; i++) {
            for (StopResult[] stopResults : list) {
                if (stopResults[i] != StopResult.WAIT_OR_IN_EXECUTING_NOT_FOUND) {
                    result[i] = stopResults[i];
                    break;
                }
            }
            result[i] = StopResult.WAIT_OR_IN_EXECUTING_NOT_FOUND;
        }
        return result;
    }
}
