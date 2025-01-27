package cn.bcd.dataProcess.parse;

import cn.bcd.dataProcess.parse.gb32960.SaveHandler_gb32960;
import cn.bcd.dataProcess.parse.gb32960.WorkHandler_gb32960;
import cn.bcd.dataProcess.parse.kafka.ext.KafkaProp;
import cn.bcd.dataProcess.parse.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.dataProcess.parse.kafka.ext.datadriven.WorkExecutor;
import cn.bcd.dataProcess.parse.kafka.ext.datadriven.WorkHandler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataConsumer extends DataDrivenKafkaConsumer implements CommandLineRunner {

    public DataConsumer(ParseProp parseProp, KafkaProp kafkaProp) {
        super("dataConsumer",
                kafkaProp.consumer,
                Runtime.getRuntime().availableProcessors(),
                0,
                null,
                100000,
                true,
                0,
                WorkHandlerScanner.get(300, 300),
                3,
                parseProp.topic);
    }

    @Override
    public WorkHandler newHandler(String id, WorkExecutor executor) {
        return new WorkHandler_gb32960(id, executor, this);
    }

    @Override
    public String monitor_log() {
        String s = super.monitor_log();
        return s + " saveQueue_gb32960[" + SaveHandler_gb32960.queue.size() + "]";
    }

    @Override
    public void run(String... args) throws Exception {
        init();
    }
}
