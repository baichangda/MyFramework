package cn.bcd.lib.base.kafka.ext;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.kafka.KafkaUtil;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class KafkaExtUtil {

    static Logger logger = LoggerFactory.getLogger(KafkaExtUtil.class);


    public record ConsumerThreadHolder(Thread thread, Thread[] threads) {

    }


    private static String getConsumerThreadName(String name, int threadIndex, int threadNum, int... partitions) {
        String partitionDesc;
        if (partitions.length == 0) {
            partitionDesc = "all";
        } else {
            partitionDesc = Arrays.stream(partitions).mapToObj(e -> e + "").collect(Collectors.joining(","));
        }
        return name + "-consumer(" + (threadIndex + 1) + "/" + threadNum + ")-partition(" + partitionDesc + ")";
    }


    /**
     * 启动消费者
     *
     * @param consumerProp
     */
    public static ConsumerThreadHolder startConsumer(String name,
                                                     String topic,
                                                     Map<String, Object> consumerProp,
                                                     PartitionMode partitionMode,
                                                     Consumer<KafkaConsumer<String, byte[]>> kafkaConsumerConsumer) {
        Thread consumeThread = null;
        Thread[] consumeThreads = null;
        switch (partitionMode.mode) {
            case 0 -> {
                /**
                 * 启动单线程、一个消费者、使用{@link KafkaConsumer#subscribe(Pattern)}完成订阅这个topic的所有分区
                 */
                final KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                consumer.subscribe(Collections.singletonList(topic), new ConsumerRebalanceLogger(consumer));
                if (partitionMode.seekToBeginning) {
                    KafkaUtil.consumerSeekToBeginning(consumer);
                }
                //初始化消费线程、提交消费任务
                String threadName = getConsumerThreadName(name, 0, 1);
                consumeThread = new Thread(() -> kafkaConsumerConsumer.accept(consumer), threadName);
                consumeThread.start();
                logger.info("start consumer[{}] for topic[{}]", threadName, topic);
            }
            case 1 -> {
                /**
                 * 启动单线程、一个消费者、使用{@link KafkaConsumer#assign(Collection)}完成订阅多个分区
                 */
                final int[] partitions = partitionMode.partitions;
                final KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                List<TopicPartition> topicPartitions = Arrays.stream(partitions).mapToObj(i -> new TopicPartition(topic, i)).toList();
                consumer.assign(topicPartitions);
                if (partitionMode.seekToBeginning) {
                    KafkaUtil.consumerSeekToBeginning(consumer);
                }
                String threadName = getConsumerThreadName(name, 0, 1, partitions);
                consumeThread = new Thread(() -> kafkaConsumerConsumer.accept(consumer), threadName);
                consumeThread.start();
                logger.info("start consumer threadName[{}] for topic[{}] assign partitions[{}]", threadName, topic, Arrays.stream(partitions).mapToObj(e -> topic + ":" + e).collect(Collectors.joining(",")));
            }
            case 2 -> {
                /**
                 * 根据指定分区个数启动对应个数的线程、每个线程一个消费者、每个消费者使用{@link KafkaConsumer#assign(Collection)}订阅一个分区
                 */
                final int[] partitions = partitionMode.partitions;
                consumeThreads = new Thread[partitions.length];
                for (int i = 0; i < partitions.length; i++) {
                    final KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                    consumer.assign(Collections.singletonList(new TopicPartition(topic, partitions[i])));
                    if (partitionMode.seekToBeginning) {
                        KafkaUtil.consumerSeekToBeginning(consumer);
                    }
                    String threadName = getConsumerThreadName(name, i, partitions.length, partitions[i]);
                    consumeThreads[i] = new Thread(() -> kafkaConsumerConsumer.accept(consumer), threadName);
                    consumeThreads[i].start();
                    logger.info("start consumer threadName[{}] for topic [{}] assign partitions[{}]", threadName, topic, partitions[i]);
                }
            }
            case 3 -> {
                /**
                 * 首先通过{@link KafkaConsumer#partitionsFor(String)}获取分区个数、然后启动对应的消费线程、每一个线程一个消费者使用{@link KafkaConsumer#assign(Collection)}订阅一个分区
                 */
                final KafkaConsumer<String, byte[]> first = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                int[] ps = first.partitionsFor(topic).stream().mapToInt(PartitionInfo::partition).toArray();
                consumeThreads = new Thread[ps.length];
                for (int i = 0; i < ps.length; i++) {
                    final KafkaConsumer<String, byte[]> consumer;
                    if (i == 0) {
                        consumer = first;
                    } else {
                        consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                    }
                    consumer.assign(Collections.singletonList(new TopicPartition(topic, ps[i])));
                    if (partitionMode.seekToBeginning) {
                        KafkaUtil.consumerSeekToBeginning(consumer);
                    }
                    String threadName = getConsumerThreadName(name, i, ps.length, ps[i]);
                    consumeThreads[i] = new Thread(() -> kafkaConsumerConsumer.accept(consumer), threadName);
                    consumeThreads[i].start();
                    logger.info("start consumer threadName[{}] for topic [{}] assign partitions[{}]", threadName, topic, ps[i]);
                }
            }
            default ->
                    throw BaseException.get("DataDrivenKafkaConsumer[{}] partitionMode[{}] not support", name, partitionMode.mode);
        }
        return new ConsumerThreadHolder(consumeThread, consumeThreads);
    }

}
