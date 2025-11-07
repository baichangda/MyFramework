package cn.bcd.lib.spring.kafka.ext;

import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.util.Collection;
import java.util.regex.Pattern;

public class ConsumerParam {
    /**
     * 0、启动单线程、一个消费者、使用{@link KafkaConsumer#subscribe(Pattern)}完成订阅这个topic的所有分区
     * 1、启动单线程、一个消费者、使用{@link KafkaConsumer#assign(Collection)}完成订阅多个分区
     * 2、根据指定分区个数启动对应个数的线程、每个线程一个消费者、每个消费者使用{@link KafkaConsumer#assign(Collection)}订阅一个分区
     * 3、首先通过{@link KafkaConsumer#partitionsFor(String)}获取分区个数、然后启动对应的消费线程、每一个线程一个消费者使用{@link KafkaConsumer#assign(Collection)}订阅一个分区
     */
    public final int mode;
    public final int[] partitions;

    /**
     * 控制当前消费者的各个分区从某处开始消费
     * -2: 不从头开始消费、默认消费模式
     * -1: 从头开始消费
     * 其他: 从指定的时间戳开始消费
     */
    public final long seekTimestamp;

    private ConsumerParam(int mode, int[] partitions, long seekTimestamp) {
        this.mode = mode;
        this.partitions = partitions;
        this.seekTimestamp = seekTimestamp;
    }

    public static ConsumerParam get(int mode, int... partitions) {
        return new ConsumerParam(mode, partitions, -2);
    }

    /**
     * 默认从最新位置开始消费
     * @param mode
     * @param partitions
     * @return
     */
    public static ConsumerParam get_seekToBeginning(int mode, int... partitions) {
        return new ConsumerParam(mode, partitions, -1);
    }

    /**
     * 默认从指定时间戳开始消费
     * @param mode
     * @param seekTimestamp
     * @param partitions
     * @return
     */
    public static ConsumerParam get_seekToTimestamp(int mode, long seekTimestamp, int... partitions) {
        return new ConsumerParam(mode, partitions, seekTimestamp);
    }
}