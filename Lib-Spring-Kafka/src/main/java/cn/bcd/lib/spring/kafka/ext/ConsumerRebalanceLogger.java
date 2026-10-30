package cn.bcd.lib.spring.kafka.ext;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * 记录 subscribe 模式下的分区分配变化，由 Kafka 在消费线程中调用。
 * 只负责日志，不等待业务工作队列排空，也不提交 offset 或转移 Handler 状态。
 * assign 模式不使用该监听器进行自动分区协调。
 */
public class ConsumerRebalanceLogger implements ConsumerRebalanceListener {
    static final Logger logger = LoggerFactory.getLogger(ConsumerRebalanceLogger.class);

    final Consumer<String, byte[]> consumer;

    public ConsumerRebalanceLogger(Consumer<String, byte[]> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        String msg = partitions.stream().map(e -> e.topic() + ":" + e.partition()).reduce((e1, e2) -> e1 + ";" + e2).orElse("");
        logger.info("consumer[{}] onPartitionsRevoked [{}]", consumer.toString(), msg);
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        String msg = partitions.stream().map(e -> e.topic() + ":" + e.partition()).reduce((e1, e2) -> e1 + ";" + e2).orElse("");
        logger.info("consumer[{}] onPartitionsAssigned [{}]", consumer.toString(), msg);
    }
}
