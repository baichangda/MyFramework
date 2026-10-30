package cn.bcd.lib.spring.kafka.ext;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.Arrays;
import java.util.Collection;

/**
 * 消费模式、订阅目标和起始位置的参数构建，不创建连接、不启动线程。
 * 工厂方法确定消费模式，seek 方法修改起始位置并返回当前对象，便于链式配置。
 * ConsumerGroup 构造时复制这些参数；之后修改原参数仅影响后续新建的启动实例。
 * topic/分区数组仍是调用方传入的数组，构建和启动快照期间应避免并发修改。
 */
public class ConsumerParam {
    /**
     * 1、启动单线程、一个消费者、使用{@link KafkaConsumer#subscribe(Collection)}消费{@link #topics}
     * 2、根据{@link #topics}个数启动对应个数的线程、每个线程一个消费者、每个消费者使用{@link KafkaConsumer#subscribe(Collection)}消费一个topic
     * 3、启动单线程、一个消费者、使用{@link KafkaConsumer#assign(Collection)}消费{@link #topicPartitions}
     * 4、根据{@link #topicPartitions}个数启动对应个数的线程、每个线程一个消费者、每个消费者使用{@link KafkaConsumer#assign(Collection)}订阅一个分区
     */
    public final int mode;
    /** 模式 1、2 使用，模式 3、4 为 null。 */
    public final String[] topics;
    /** 模式 3、4 使用，模式 1、2 为 null。 */
    public final TopicPartition[] topicPartitions;

    /**
     * 控制当前消费者的各个分区从某处开始消费
     * -3: 从末尾开始消费
     * -2: 从头开始消费
     * -1: 默认消费模式
     * 非负值: 从指定的 Unix 时间戳（毫秒）开始消费
     * -1 保留 Kafka 的已提交位点和 auto.offset.reset 策略；显式 seek 会覆盖该起始位置。
     * seek 仅影响本次连接的本地消费位置，不在参数构建时提交或修改消费组位点。
     */
    public long seekTimestamp = -1;

    private ConsumerParam(int mode, String[] topics, TopicPartition[] topicPartitions) {
        this.mode = mode;
        this.topics = topics;
        this.topicPartitions = topicPartitions;
    }

    /**
     * 构建单消费者参数：传入分区时使用 assign，未传分区时使用 subscribe。
     * subscribe 模式下实际分区由消费组分配；同组有其他实例时不保证分到该 topic 的全部分区。
     *
     * @param topic 消费的 topic
     * @param partitions 显式指定的分区编号，不传则使用消费组自动分配
     * @return 单消费者参数
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
     * 构建模式 1 参数：一个消费线程订阅所有指定 topic，参与消费组的自动分区分配。
     *
     * @param topics 订阅的 topic 集合
     * @return 单线程订阅参数
     */
    public static ConsumerParam get_singleConsumer_subscribeTopics(String... topics) {
        return new ConsumerParam(1, topics, null);
    }

    /**
     * 构建模式 2 参数：每个 topic 使用独立消费线程，线程数量不随分区数量自动扩展。
     *
     * @param topics 每个元素对应一个消费者的订阅目标
     * @return 按 topic 拆分消费者的参数
     */
    public static ConsumerParam get_multipleConsumer_subscribeTopics(String... topics) {
        return new ConsumerParam(2, topics, null);
    }


    /**
     * 构建模式 3 参数：一个消费线程直接消费所有指定分区，不参与组内自动分区分配。
     *
     * @param topicPartitions 显式消费的 topic/分区集合
     * @return 单线程指定分区参数
     */
    public static ConsumerParam get_singleConsumer_assignTopicPartitions(TopicPartition... topicPartitions) {
        return new ConsumerParam(3, null, topicPartitions);
    }

    /**
     * 构建模式 4 参数：每个指定分区使用独立消费线程，线程数量等于分区数量。
     *
     * @param topicPartitions 每个元素对应一个消费者的指定分区
     * @return 按分区拆分消费者的参数
     */
    public static ConsumerParam get_multipleConsumer_assignTopicPartitions(TopicPartition... topicPartitions) {
        return new ConsumerParam(4, null, topicPartitions);
    }

    /**
     * 请求从各分区当前可用的最早位置开始消费，在连接初始化时执行 seek。
     *
     * @return 当前参数对象
     */
    public ConsumerParam seekToBeginning() {
        this.seekTimestamp = -2;
        return this;
    }

    /**
     * 请求从连接初始化时各分区的末尾开始消费，跳过此前已有消息。
     *
     * @return 当前参数对象
     */
    public ConsumerParam seekToEnd() {
        this.seekTimestamp = -3;
        return this;
    }

    /**
     * 请求从各分区首条时间戳不早于指定值的消息开始消费；未找到时定位到该分区末尾。
     *
     * @param timestamp Unix 时间戳，单位毫秒，通常为非负值
     * @return 当前参数对象
     */
    public ConsumerParam seekToTimestamp(long timestamp) {
        this.seekTimestamp = timestamp;
        return this;
    }
}
