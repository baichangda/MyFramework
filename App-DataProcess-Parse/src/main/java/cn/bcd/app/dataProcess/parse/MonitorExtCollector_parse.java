package cn.bcd.app.dataProcess.parse;

import cn.bcd.lib.spring.monitor.client.MonitorExtCollector;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MonitorExtCollector_parse implements MonitorExtCollector {

    public static int blockingNum;
    public static double consumeSpeed;
    public static String workQueues;
    public static double workSpeed;
    public static int saveQueue;
    public static double saveSpeed;

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
