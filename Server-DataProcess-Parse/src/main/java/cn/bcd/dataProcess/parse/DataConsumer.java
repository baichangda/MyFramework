package cn.bcd.dataProcess.parse;

import cn.bcd.base.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.base.kafka.ext.datadriven.WorkExecutor;
import cn.bcd.base.kafka.ext.datadriven.WorkHandler;
import cn.bcd.dataProcess.parse.gb32960.SaveHandler_gb32960;
import cn.bcd.dataProcess.parse.gb32960.WorkHandler_gb32960;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

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
        init(kafkaProperties.getConsumer());
    }
}
