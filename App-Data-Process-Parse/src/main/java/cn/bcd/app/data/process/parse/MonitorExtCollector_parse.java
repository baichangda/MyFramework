package cn.bcd.app.data.process.parse;

import cn.bcd.lib.monitor.client.MonitorExtCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MonitorExtCollector_parse implements MonitorExtCollector {

    @Autowired
    DataConsumer dataConsumer;

    public static int blockingNum;
    public static double consumeSpeed;
    public static int workQueueTaskNum;
    public static double workSpeed;
    public static int saveQueueTaskNum_gb32960;
    public static double saveSpeed_gb32960;

    @Override
    public Map<String, Object> collect() {
        return Map.of("blockingNum", blockingNum,
                "consumeSpeed", consumeSpeed,
                "workQueueTaskNum", workQueueTaskNum,
                "workSpeed", workSpeed,
                "saveQueueTaskNum_gb32960", saveQueueTaskNum_gb32960,
                "saveSpeed_gb32960", saveSpeed_gb32960);
    }
}
