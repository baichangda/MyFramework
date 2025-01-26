package cn.bcd.dataProcess.parse;

import cn.bcd.dataProcess.parse.gb32960.WorkHandler_gb32960;
import cn.bcd.dataProcess.parse.kafka.ext.ConsumerProp;
import cn.bcd.dataProcess.parse.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.dataProcess.parse.kafka.ext.datadriven.WorkExecutor;
import cn.bcd.dataProcess.parse.kafka.ext.datadriven.WorkHandler;
import org.springframework.stereotype.Component;

@Component
public class DataConsumer extends DataDrivenKafkaConsumer {

    public DataConsumer(ParseProp parseProp) {
        super("dataConsumer",
                new ConsumerProp(),
                Runtime.getRuntime().availableProcessors(),
                0,
                null,
                100000,
                false,
                0,
                WorkHandlerScanner.get(300, 300),
                3,
                parseProp.topic);
    }

    @Override
    public WorkHandler newHandler(String id, WorkExecutor executor) {
        return new WorkHandler_gb32960(id, executor);
    }
}
