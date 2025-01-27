package cn.bcd.dataProcess.parse;

import cn.bcd.base.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.base.kafka.ext.datadriven.WorkExecutor;
import cn.bcd.base.kafka.ext.datadriven.WorkHandler;
import cn.bcd.base.util.FloatUtil;
import cn.bcd.base.util.StringUtil;
import cn.bcd.dataProcess.parse.gb32960.SaveHandler_gb32960;
import cn.bcd.dataProcess.parse.gb32960.WorkHandler_gb32960;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

@EnableConfigurationProperties({ParseProp.class, KafkaProperties.class})
@Component
public class DataConsumer extends DataDrivenKafkaConsumer implements CommandLineRunner {

    @Autowired
    KafkaProperties kafkaProperties;

    public DataConsumer(ParseProp parseProp) {
        super("dataConsumer",
                Runtime.getRuntime().availableProcessors(),
                0,
                null,
                100000,
                true,
                0,
                WorkHandlerScanner.get(300, 300),
                5,
                parseProp.topic);
    }

    @Override
    public WorkHandler newHandler(String id, WorkExecutor executor) {
        return new WorkHandler_gb32960(id, executor, this);
    }

    @Override
    public String monitor_log() {
        double period = monitor_period;
        int workExecutorCount = workExecutors.length;
        long workHandlerCount = monitor_workHandlerCount.sum();
        long curBlockingNum = blockingNum.sum();
        double consumeSpeed = FloatUtil.format(monitor_consumeCount.sumThenReset() / period, 2);
        int workQueueTaskNum = 0;
        StringJoiner workQueueStatus = new StringJoiner(" ");
        for (WorkExecutor workExecutor : workExecutors) {
            int size = workExecutor.blockingQueue.size();
            workQueueTaskNum += size;
            workQueueStatus.add(size + (workExecutorQueueSize > 0 ? ("/" + workExecutorQueueSize) : ""));
        }
        double workSpeed = FloatUtil.format(monitor_workCount.sumThenReset() / period, 2);
        int saveQueueTaskNum_gb32960 = SaveHandler_gb32960.queue.size();
        double saveSpeed_gb32960 = FloatUtil.format(SaveHandler_gb32960.saveCount.sumThenReset() / period, 2);

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
                workQueueStatus.toString(),
                workSpeed,
                saveQueueTaskNum_gb32960,
                saveSpeed_gb32960);
    }

    @Override
    public void run(String... args) throws Exception {
        init(kafkaProperties.getConsumer());
    }
}
