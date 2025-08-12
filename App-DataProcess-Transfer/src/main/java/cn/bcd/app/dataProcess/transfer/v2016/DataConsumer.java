package cn.bcd.app.dataProcess.transfer.v2016;

import cn.bcd.app.dataProcess.transfer.v2016.handler.KafkaDataHandler;
import cn.bcd.app.dataProcess.transfer.v2016.handler.TcpDataHandler;
import cn.bcd.app.dataProcess.transfer.v2016.handler.TransferDataHandler;
import cn.bcd.app.dataProcess.transfer.v2016.tcp.Client;
import cn.bcd.lib.base.executor.BlockingChecker;
import cn.bcd.lib.base.kafka.ext.PartitionMode;
import cn.bcd.lib.base.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.lib.base.kafka.ext.datadriven.WorkHandler;
import cn.bcd.lib.base.util.FloatUtil;
import cn.bcd.lib.base.util.StringUtil;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataConsumer extends DataDrivenKafkaConsumer {

    List<KafkaDataHandler> kafkaDataHandlers;
    List<TcpDataHandler> tcpDataHandlers;

    KafkaProperties kafkaProp;

    public DataConsumer(KafkaProperties kafkaProp, String topic, int[] partitions,
                        List<KafkaDataHandler> kafkaDataHandlers,
                        List<TcpDataHandler> tcpDataHandlers) {
        super("DataConsumer",
                Runtime.getRuntime().availableProcessors(),
                false,
                BlockingChecker.DEFAULT,
                100000,
                true,
                0,
                WorkHandlerScanner.get(300, 60),
                5,
                topic,
                PartitionMode.get(1, partitions)
        );
        this.kafkaProp = kafkaProp;
        this.kafkaDataHandlers = kafkaDataHandlers;
        this.tcpDataHandlers = tcpDataHandlers;
    }

    public void init() {
        Map<String, Object> consumerProp = kafkaProp.getConsumer().buildProperties(new DefaultSslBundleRegistry());
        //初始暂停消费
        pauseConsume();
        init(consumerProp);
    }

    @Override
    public WorkHandler newHandler(String id, byte[] first) {
        return new TransferDataHandler(id, kafkaDataHandlers, tcpDataHandlers);
    }

    @Override
    public String monitor_log() {
        int workExecutorCount = workExecutors.length;
        long workHandlerCount = monitor_workHandlerCount.sum();
        long curBlockingNum = blockingNum.sum();
        double consumeSpeed = FloatUtil.format(monitor_consumeCount.sumThenReset() / ((double) monitor_period), 2);
        String workQueueStatus = Arrays.stream(workExecutors).map(e -> e.blockingQueue.size() + "").collect(Collectors.joining(" "));
        double workSpeed = FloatUtil.format(monitor_workCount.sumThenReset() / ((double) monitor_period), 2);
        double sendSpeed = FloatUtil.format(Client.sendNum.sumThenReset() / ((double) monitor_period), 2);
        return StringUtil.format("name[{}] " +
                        "workExecutor[{}] " +
                        "workHandler[{}] " +
                        "blocking[{}/{}] " +
                        "consumeSpeed[{}/s] " +
                        "queues[{}] " +
                        "workSpeed[{}/s] " +
                        "sendQueue[{}/{}] " +
                        "sendSpeed[{}/s]",
                name,
                workExecutorCount,
                workHandlerCount,
                curBlockingNum, maxBlockingNum,
                consumeSpeed,
                workQueueStatus,
                workSpeed,
                Client.sendQueue.size(), Client.SEND_QUEUE_SIZE,
                sendSpeed
        );
    }
}
