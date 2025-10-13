package cn.bcd.app.dataProcess.transfer.v2016;

import cn.bcd.app.dataProcess.transfer.v2016.handler.KafkaDataHandler;
import cn.bcd.app.dataProcess.transfer.v2016.handler.TransferDataHandler;
import cn.bcd.app.dataProcess.transfer.v2016.tcp.TcpClient;
import cn.bcd.lib.base.kafka.ext.ConsumerParam;
import cn.bcd.lib.base.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.lib.base.kafka.ext.datadriven.WorkHandler;
import cn.bcd.lib.base.util.FloatUtil;
import cn.bcd.lib.base.util.StringUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataConsumer extends DataDrivenKafkaConsumer {
    List<KafkaDataHandler> kafkaDataHandlers;
    KafkaProperties kafkaProp;

    public DataConsumer(KafkaProperties kafkaProp, String topic, int[] partitions,
                        List<KafkaDataHandler> kafkaDataHandlers) {
        super("DataConsumer",
                Runtime.getRuntime().availableProcessors(),
                false,
                null,
                100000,
                true,
                0,
                WorkHandlerScanner.get(300, 60),
                5,
                topic,
                ConsumerParam.get(1, partitions)
        );
        this.kafkaProp = kafkaProp;
        this.kafkaDataHandlers = kafkaDataHandlers;
    }

    public void init() {
        Map<String, Object> consumerProp = kafkaProp.getConsumer().buildProperties(new DefaultSslBundleRegistry());
        //暂停消费、等待连接
        pauseConsume();
        init(consumerProp);
    }

    @Override
    public WorkHandler newHandler(String id, ConsumerRecord<String, byte[]> first) {
        return new TransferDataHandler(id, kafkaDataHandlers);
    }

    @Override
    public String monitor_log() {
        int workExecutorCount = workExecutors.length;
        long workHandlerCount = monitor_workHandlerCount.sum();
        long curBlockingNum = blockingNum.sum();
        double consumeSpeed = FloatUtil.format(monitor_consumeCount.sumThenReset() / ((double) monitor_period), 2);
        String workQueueStatus = Arrays.stream(workExecutors).map(e -> e.blockingQueue.size() + "").collect(Collectors.joining(" "));
        double workSpeed = FloatUtil.format(monitor_workCount.sumThenReset() / ((double) monitor_period), 2);
        double sendSpeed = FloatUtil.format(TcpClient.sendNum.sumThenReset() / ((double) monitor_period), 2);
        double saveSpeed_transfer = FloatUtil.format(SaveUtil.saveCount_transfer.sumThenReset() / ((double) monitor_period), 2);
        double saveSpeed_transferResponse = FloatUtil.format(SaveUtil.saveCount_transferResponse.sumThenReset() / ((double) monitor_period), 2);
        return StringUtil.format("name[{}] " +
                        "workExecutor[{}] " +
                        "workHandler[{}] " +
                        "blocking[{}/{}] " +
                        "consumeSpeed[{}/s] " +
                        "queues[{}] " +
                        "workSpeed[{}/s] " +
                        "sendQueue[{}/{}] " +
                        "sendSpeed[{}/s]" +
                        "saveQueue_transfer[{}/{}] " +
                        "saveSpeed_transfer[{}/s]" +
                        "saveQueue_transferResponse[{}/{}] " +
                        "saveSpeed_transferResponse[{}/s]",
                name,
                workExecutorCount,
                workHandlerCount,
                curBlockingNum, maxBlockingNum,
                consumeSpeed,
                workQueueStatus,
                workSpeed,
                TcpClient.sendQueue.size(), TcpClient.queueSize,
                sendSpeed,
                SaveUtil.queue_transfer.size(), SaveUtil.queueSize,
                saveSpeed_transfer,
                SaveUtil.queue_transferResponse.size(), SaveUtil.queueSize,
                saveSpeed_transferResponse
        );
    }
}
