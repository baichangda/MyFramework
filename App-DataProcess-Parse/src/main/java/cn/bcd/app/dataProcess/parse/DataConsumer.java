package cn.bcd.app.dataProcess.parse;

import cn.bcd.app.dataProcess.parse.v2016.DataHandler_v2025;
import cn.bcd.app.dataProcess.parse.v2016.WorkHandler_v2016;
import cn.bcd.app.dataProcess.parse.v2025.WorkHandler_v2025;
import cn.bcd.lib.base.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.lib.base.kafka.ext.datadriven.WorkHandler;
import cn.bcd.lib.base.util.FloatUtil;
import cn.bcd.lib.base.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@EnableConfigurationProperties({ParseProp.class, KafkaProperties.class})
@Component
public class DataConsumer extends DataDrivenKafkaConsumer implements CommandLineRunner {

    @Autowired
    KafkaProperties kafkaProperties;

    List<DataHandler_v2025> handlers_v2016;
    List<cn.bcd.app.dataProcess.parse.v2025.DataHandler_v2025> handlers_v2025;

    public DataConsumer(ParseProp parseProp, List<DataHandler_v2025> handlers_v2016, List<cn.bcd.app.dataProcess.parse.v2025.DataHandler_v2025> handlers_v2025) {
        super("dataConsumer",
                Runtime.getRuntime().availableProcessors(),
                false,
                null,
                100000,
                true,
                0,
                WorkHandlerScanner.get(300, 300),
                5,
                parseProp.topic,
                null);
        logger.info("""
                ---------DataHandler_v2016---------
                {}
                -----------------------------------
                """, handlers_v2016.stream().map(e -> e.getClass().getName()).collect(Collectors.joining("\n")));

        logger.info("""
                ---------DataHandler_v2025---------
                {}
                -----------------------------------
                """, handlers_v2016.stream().map(e -> e.getClass().getName()).collect(Collectors.joining("\n")));
    }

    public DataConsumer(ParseProp parseProp) {
        super("dataConsumer",
                Runtime.getRuntime().availableProcessors(),
                false,
                null,
                100000,
                true,
                0,
                WorkHandlerScanner.get(300, 300),
                5,
                parseProp.topic,
                null);
    }

    @Override
    public WorkHandler newHandler(String id, byte[] first) {
        if (first[16] == 0x23 && first[17] == 0x23) {
            return new WorkHandler_v2016(id, handlers_v2016);
        } else {
            return new WorkHandler_v2025(id, handlers_v2025);
        }
    }

    @Override
    public String monitor_log() {
        double period = monitor_period;
        int workExecutorCount = workExecutors.length;
        long workHandlerCount = monitor_workHandlerCount.sum();
        long curBlockingNum = blockingNum.sum();
        double consumeSpeed = FloatUtil.format(monitor_consumeCount.sumThenReset() / period, 2);
        int workQueueTaskNum = 0;
        String workQueueStatus = Arrays.stream(workExecutors).map(e -> e.blockingQueue.size() + "").collect(Collectors.joining(" "));
        double workSpeed = FloatUtil.format(monitor_workCount.sumThenReset() / period, 2);
        int saveQueueTaskNum_gb32960 = SaveUtil.queue.size();
        double saveSpeed_gb32960 = FloatUtil.format(SaveUtil.saveCount.sumThenReset() / period, 2);

        MonitorExtCollector_parse.blockingNum = (int) curBlockingNum;
        MonitorExtCollector_parse.consumeSpeed = consumeSpeed;
        MonitorExtCollector_parse.workQueueTaskNum = workQueueTaskNum;
        MonitorExtCollector_parse.workSpeed = workSpeed;
        MonitorExtCollector_parse.saveQueueTaskNum_gb32960 = saveQueueTaskNum_gb32960;
        MonitorExtCollector_parse.saveSpeed_gb32960 = saveSpeed_gb32960;

        return StringUtil.format("name[{}] " +
                        "workExecutor[{}] " +
                        "workHandler[{}] " +
                        "blocking[{}/{}] " +
                        "consumeSpeed[{}/s] " +
                        "workQueueTaskNum[{}] " +
                        "queues[{}] " +
                        "workSpeed[{}/s] " +
                        "saveQueueTaskNum_gb32960[{}] " +
                        "saveSpeed_gb32960[{}/s]",
                name,
                workExecutorCount,
                workHandlerCount,
                curBlockingNum, maxBlockingNum,
                consumeSpeed,
                workQueueTaskNum,
                workQueueStatus,
                workSpeed,
                saveQueueTaskNum_gb32960,
                saveSpeed_gb32960);
    }

    @Override
    public void run(String... args) {
        init(kafkaProperties.getConsumer().buildProperties(new DefaultSslBundleRegistry()));
    }
}
