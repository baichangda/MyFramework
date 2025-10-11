package cn.bcd.lib.base.kafka.ext;

import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.util.Collection;
import java.util.regex.Pattern;

public class PartitionMode {
    /**
     * 0、启动单线程、一个消费者、使用{@link KafkaConsumer#subscribe(Pattern)}完成订阅这个topic的所有分区
     * 1、启动单线程、一个消费者、使用{@link KafkaConsumer#assign(Collection)}完成订阅多个分区
     * 2、根据指定分区个数启动对应个数的线程、每个线程一个消费者、每个消费者使用{@link KafkaConsumer#assign(Collection)}订阅一个分区
     * 3、首先通过{@link KafkaConsumer#partitionsFor(String)}获取分区个数、然后启动对应的消费线程、每一个线程一个消费者使用{@link KafkaConsumer#assign(Collection)}订阅一个分区
     */
    public final int mode;
    public final int[] partitions;
    //是否从头开始消费
    public final boolean seekToBeginning;

    private PartitionMode(int mode, int[] partitions, boolean seekToBeginning) {
        this.mode = mode;
        this.partitions = partitions;
        this.seekToBeginning = seekToBeginning;
    }

    public static PartitionMode get(int mode, int... partitions) {
        return new PartitionMode(mode, partitions, false);
    }

    public static PartitionMode get_seekToBeginning(int mode, int... partitions) {
        return new PartitionMode(mode, partitions, true);
    }
}