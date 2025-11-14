package cn.bcd.lib.spring.kafka.ext;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.Arrays;
import java.util.Collection;

public class ConsumerParam {
    /**
     * 1、启动单线程、一个消费者、使用{@link KafkaConsumer#subscribe(Collection)}消费{@link #topics}
     * 2、根据{@link #topics}个数启动对应个数的线程、每个线程一个消费者、每个消费者使用{@link KafkaConsumer#subscribe(Collection)}消费一个topic
     * 3、启动单线程、一个消费者、使用{@link KafkaConsumer#assign(Collection)}消费{@link #topicPartitions}
     * 4、根据{@link #topicPartitions}个数启动对应个数的线程、每个线程一个消费者、每个消费者使用{@link KafkaConsumer#assign(Collection)}订阅一个分区
     */
    public final int mode;
    public final String[] topics;
    public final TopicPartition[] topicPartitions;

    /**
     * 控制当前消费者的各个分区从某处开始消费
     * -2: 不从头开始消费、默认消费模式
     * -1: 从头开始消费
     * 其他: 从指定的时间戳开始消费
     */
    public long seekTimestamp = -2;

    private ConsumerParam(int mode, String[] topics, TopicPartition[] topicPartitions) {
        this.mode = mode;
        this.topics = topics;
        this.topicPartitions = topicPartitions;
    }

    /**
     * 启动单线程、一个消费者、消费topic的指定分区
     * 如果分区参数为空、则消费topic的所有分区
     * @param topic
     * @param partitions
     * @return
     */
    public static ConsumerParam get_singleConsumer(String topic, int... partitions) {
        if (partitions.length == 0) {
            return get_singleConsumer_subscribeTopics(topic);
        } else {
            TopicPartition[] arr = Arrays.stream(partitions).mapToObj(e -> new TopicPartition(topic, e)).toArray(TopicPartition[]::new);
            return get_singleConsumer_assignTopicPartitions(arr);
        }
    }

    /**
     * 启动单线程、一个消费者、使用{@link KafkaConsumer#subscribe(Collection)}消费{@link #topics}
     *
     * @param topics
     * @return
     */
    public static ConsumerParam get_singleConsumer_subscribeTopics(String... topics) {
        return new ConsumerParam(1, topics, null);
    }

    /**
     * 启动多线程、多个消费者、使用{@link KafkaConsumer#subscribe(Collection)}消费{@link #topics}
     *
     * @param topics
     * @return
     */
    public static ConsumerParam get_multipleConsumer_subscribeTopics(String... topics) {
        return new ConsumerParam(2, topics, null);
    }


    /**
     * 启动单线程、一个消费者、使用{@link KafkaConsumer#assign(Collection)}订阅{@link #topicPartitions}
     *
     * @param topicPartitions
     * @return
     */
    public static ConsumerParam get_singleConsumer_assignTopicPartitions(TopicPartition... topicPartitions) {
        return new ConsumerParam(3, null, topicPartitions);
    }

    /**
     * 启动多线程、多个消费者、使用{@link KafkaConsumer#assign(Collection)}订阅{@link #topicPartitions}
     *
     * @param topicPartitions
     * @return
     */
    public static ConsumerParam get_multipleConsumer_assignTopicPartitions(TopicPartition... topicPartitions) {
        return new ConsumerParam(4, null, topicPartitions);
    }

    /**
     * 使所有的{@link KafkaConsumer}从头开始消费
     *
     * @return
     */
    public ConsumerParam seekToBeginning() {
        this.seekTimestamp = -1;
        return this;
    }

    /**
     * 使所有的{@link KafkaConsumer}从指定的时间戳开始消费
     *
     * @return
     */
    public ConsumerParam seekToTimestamp(long timestamp) {
        this.seekTimestamp = timestamp;
        return this;
    }
}