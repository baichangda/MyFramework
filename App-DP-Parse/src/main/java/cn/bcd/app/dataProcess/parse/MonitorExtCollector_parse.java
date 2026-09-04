package cn.bcd.app.dataProcess.parse;

import cn.bcd.lib.spring.monitor.client.MonitorExtCollector;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MonitorExtCollector_parse implements MonitorExtCollector {

    public volatile static int blockingNum;
    public volatile static double consumeSpeed;
    public volatile static String workQueues;
    public volatile static double workSpeed;
    public volatile static int saveQueue;
    public volatile static double saveSpeed;

    @Override
    public Map<String, Object> collect() {
        return Map.of("blockingNum", blockingNum,
                "consumeSpeed", consumeSpeed,
                "workQueues", workQueues,
                "workSpeed", workSpeed,
                "saveQueue", saveQueue,
                "saveSpeed", saveSpeed);
    }
}
