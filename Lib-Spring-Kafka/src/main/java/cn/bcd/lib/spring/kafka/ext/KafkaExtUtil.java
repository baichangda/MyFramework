package cn.bcd.lib.spring.kafka.ext;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.kafka.KafkaUtil;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

public class KafkaExtUtil {

    static Logger logger = LoggerFactory.getLogger(KafkaExtUtil.class);


    public record ConsumerThreadHolder(Thread thread, Thread[] threads) {

    }


    private static String getConsumerThreadName(String name, int threadIndex, int threadNum) {
        return name + "-consumer(" + (threadIndex + 1) + "/" + threadNum + ")";
    }


    /**
     * 启动消费者
     *
     * @param consumerProp
     */
    public static ConsumerThreadHolder startConsumer(String name,
                                                     Map<String, Object> consumerProp,
                                                     ConsumerParam consumerParam,
                                                     Consumer<KafkaConsumer<String, byte[]>> kafkaConsumerConsumer) {
        long seekTimestamp = consumerParam.seekTimestamp;
        if (seekTimestamp == -1) {
            consumerProp.put("auto.offset.reset", "earliest");
        }
        Thread consumeThread = null;
        Thread[] consumeThreads = null;
        String[] topics = consumerParam.topics;
        TopicPartition[] topicPartitions = consumerParam.topicPartitions;
        switch (consumerParam.mode) {
            case 1 -> {
                final KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                consumer.subscribe(Arrays.asList(topics), new ConsumerRebalanceLogger(consumer));
                if (seekTimestamp == -1) {
                    KafkaUtil.consumerSeekToBeginning(consumer);
                } else if (seekTimestamp >= 0) {
                    KafkaUtil.consumerSeekToTimestamp(consumer, seekTimestamp);
                }
                //初始化消费线程、提交消费任务
                String threadName = getConsumerThreadName(name, 0, 1);
                consumeThread = new Thread(() -> kafkaConsumerConsumer.accept(consumer), threadName);
                consumeThread.start();
                logger.info("start consumer[{}] for topics{}", threadName, Arrays.toString(topics));
            }
            case 2 -> {
                consumeThreads = new Thread[topics.length];
                for (int i = 0; i < topics.length; i++) {
                    String topic = topics[i];
                    final KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                    consumer.subscribe(Collections.singletonList(topic), new ConsumerRebalanceLogger(consumer));
                    if (seekTimestamp == -1) {
                        KafkaUtil.consumerSeekToBeginning(consumer);
                    } else if (seekTimestamp >= 0) {
                        KafkaUtil.consumerSeekToTimestamp(consumer, seekTimestamp);
                    }
                    //初始化消费线程、提交消费任务
                    String threadName = getConsumerThreadName(name, i, topics.length);
                    consumeThreads[i] = new Thread(() -> kafkaConsumerConsumer.accept(consumer), threadName);
                    consumeThreads[i].start();
                    logger.info("start consumer[{}] for topic[{}]", threadName, topic);
                }
            }
            case 3 -> {
                final KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                consumer.assign(Arrays.asList(topicPartitions));
                if (seekTimestamp == -1) {
                    KafkaUtil.consumerSeekToBeginning(consumer);
                } else if (seekTimestamp >= 0) {
                    KafkaUtil.consumerSeekToTimestamp(consumer, seekTimestamp);
                }
                //初始化消费线程、提交消费任务
                String threadName = getConsumerThreadName(name, 0, 1);
                consumeThread = new Thread(() -> kafkaConsumerConsumer.accept(consumer), threadName);
                consumeThread.start();
                logger.info("start consumer[{}] for topicPartitions{}", threadName, Arrays.toString(topicPartitions));
            }
            case 4 -> {
                consumeThreads = new Thread[topicPartitions.length];
                for (int i = 0; i < topicPartitions.length; i++) {
                    TopicPartition topicPartition = topicPartitions[i];
                    final KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                    consumer.assign(Collections.singletonList(topicPartition));
                    if (seekTimestamp == -1) {
                        KafkaUtil.consumerSeekToBeginning(consumer);
                    } else if (seekTimestamp >= 0) {
                        KafkaUtil.consumerSeekToTimestamp(consumer, seekTimestamp);
                    }
                    String threadName = getConsumerThreadName(name, i, topicPartitions.length);
                    consumeThreads[i] = new Thread(() -> kafkaConsumerConsumer.accept(consumer), threadName);
                    consumeThreads[i].start();
                    logger.info("start consumer threadName[{}] for topicPartition[{}]", threadName, topicPartition);
                }
            }
            default ->
                    throw BaseException.get("ConsumerParam mode not support", name, consumerParam.mode);
        }
        return new ConsumerThreadHolder(consumeThread, consumeThreads);
    }

}
