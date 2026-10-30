package cn.bcd.lib.spring.kafka.ext;

import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.spring.kafka.KafkaUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 管理一组独立运行的 Kafka 消费线程，不汇总启动结果，也不等待全部初始化后统一放行。
 * 每个线程自己创建连接、订阅、seek、进入消费循环，并在退出时关闭自己的连接。
 * 单个线程初始化失败或消费循环退出时只记录异常，不停止其他线程，也不自动重建失败线程。
 */
public final class ConsumerGroup implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerGroup.class);

    /** 构造时准备全部线程，调用方保存本组对象后再 start，便于内部回调正确识别线程归属。 */
    private final Thread[] consumerThreads;
    /** 只由 close 设置；消费线程在网络初始化后再次检查，避免关闭后进入消费循环。 */
    private volatile boolean stopped;

    /**
     * 只创建参数快照和线程对象，不创建 Kafka 连接。
     *
     * @param name 线程名称前缀
     * @param properties 原生消费者配置，构造时复制
     * @param consumerParam 消费模式、topic/分区和起始位置，构造时拆分为独立快照
     * @param consume 在所属消费线程运行的消费循环，返回后关闭该线程的连接
     */
    public ConsumerGroup(String name, Map<String, Object> properties, ConsumerParam consumerParam,
                         Consumer<KafkaConsumer<String, byte[]>> consume) {
        Map<String, Object> config = new HashMap<>(properties);
        List<ConsumerParam> params = split(Objects.requireNonNull(consumerParam, "consumerParam"));
        Objects.requireNonNull(consume, "consume");
        consumerThreads = new Thread[params.size()];
        for (int i = 0; i < consumerThreads.length; i++) {
            ConsumerParam param = params.get(i);
            consumerThreads[i] = new Thread(() -> runConsumer(param, config, consume),
                    name + "-consumer(" + (i + 1) + "/" + consumerThreads.length + ")");
        }
    }

    /**
     * 发起各线程的启动后立即返回，不等待 Kafka 初始化完成。
     * 由外层 Consumer 保证每个 ConsumerGroup 只调用一次，不在这里维护重复启动状态。
     * 与 close 使用同一把锁，保证关闭后不会再启动线程。
     */
    public synchronized void start() {
        if (stopped) {
            throw new IllegalStateException("consumer group already closed");
        }
        for (Thread thread : consumerThreads) {
            thread.start();
        }
    }

    /** 所有连接操作保持在所属线程内；异常只影响当前线程，组级停止标志不在这里修改。 */
    private void runConsumer(ConsumerParam param, Map<String, Object> config,
                             Consumer<KafkaConsumer<String, byte[]>> consume) {
        try (KafkaConsumer<String, byte[]> consumer = newConsumer(param, config)) {
            // 初始化可能等待网络；期间已关闭或中断时，不再进入消费循环。
            if (stopped || Thread.currentThread().isInterrupted()) {
                return;
            }
            consume.accept(consumer);
        } catch (Exception | Error ex) {
            logger.error("kafka consumer thread error thread[{}]", Thread.currentThread().getName(), ex);
        }
    }

    /** 判断是否为本组消费线程，外层 Consumer 据此避免内部关闭调用等待自身退出。 */
    public boolean contains(Thread thread) {
        return Arrays.asList(consumerThreads).contains(thread);
    }

    /** 返回不可修改的消费线程列表，用于诊断线程数量和存活状态。 */
    public List<Thread> threads() {
        return List.of(consumerThreads);
    }

    /**
     * 阻止继续启动，先中断所有消费线程，再在锁外等待退出。
     * 内部线程调用只发出停止请求，避免等待自己；业务工作队列仍由外层 Consumer 关闭。
     * 连接通过消费线程的资源块关闭，因此消费循环应在中断时及时返回。
     */
    @Override
    public void close() {
        synchronized (this) {
            stopped = true;
            for (Thread thread : consumerThreads) {
                thread.interrupt();
            }
        }
        if (!contains(Thread.currentThread())) {
            ExecutorUtil.await((Object) consumerThreads);
        }
    }

    /**
     * 将四种模式展开为每个线程的一份单消费者参数，统一后续连接创建逻辑。
     * 单线程模式复制整个数组，多线程模式为每个目标新建数组；同时复制 seek 位置，
     * 使启动后的线程不再依赖调用方可修改的原参数。不在这里创建网络连接。
     */
    static List<ConsumerParam> split(ConsumerParam source) {
        List<ConsumerParam> params = new ArrayList<>();
        switch (source.mode) {
            case 1 -> params.add(ConsumerParam.get_singleConsumer_subscribeTopics(source.topics.clone()));
            case 2 -> {
                for (String topic : source.topics) {
                    params.add(ConsumerParam.get_singleConsumer_subscribeTopics(topic));
                }
            }
            case 3 -> params.add(ConsumerParam.get_singleConsumer_assignTopicPartitions(source.topicPartitions.clone()));
            case 4 -> {
                for (TopicPartition partition : source.topicPartitions) {
                    params.add(ConsumerParam.get_singleConsumer_assignTopicPartitions(partition));
                }
            }
            default -> throw new IllegalArgumentException("unsupported consumer mode: " + source.mode);
        }
        if (params.isEmpty()) {
            throw new IllegalArgumentException("consumer task cannot be empty");
        }
        for (ConsumerParam param : params) {
            param.seekTimestamp = source.seekTimestamp;
        }
        return List.copyOf(params);
    }

    /**
     * 在所属消费线程内创建并初始化一个消费者，成功后由 runConsumer 的资源块负责关闭。
     * 初始化尚未成功时资源尚未交给 runConsumer，必须在本方法中主动关闭。
     */
    private static KafkaConsumer<String, byte[]> newConsumer(ConsumerParam param, Map<String, Object> properties) {
        if (param.mode != 1 && param.mode != 3) {
            throw new IllegalStateException("split multi-consumer parameters before creating a consumer");
        }
        Map<String, Object> config = new HashMap<>(properties);
        if (param.seekTimestamp == -2) {
            // seek 工具会先 poll 等待分区；无已提交位点时先使用 earliest，随后再显式 seek。
            config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        }
        KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(config);
        try {
            // subscribe 参与组内自动分区分配；assign 直接指定分区，不依赖组内自动分配。
            if (param.mode == 1) {
                consumer.subscribe(Arrays.asList(param.topics), new ConsumerRebalanceLogger(consumer));
            } else {
                consumer.assign(Arrays.asList(param.topicPartitions));
            }
            if (param.seekTimestamp == -3) {
                KafkaUtil.consumerSeekToEnd(consumer);
            } else if (param.seekTimestamp == -2) {
                KafkaUtil.consumerSeekToBeginning(consumer);
            } else if (param.seekTimestamp >= 0) {
                KafkaUtil.consumerSeekToTimestamp(consumer, param.seekTimestamp);
            }
            return consumer;
        } catch (RuntimeException | Error ex) {
            try {
                consumer.close();
            } catch (RuntimeException | Error closeError) {
                // 保留最初的初始化错误作为主异常，关闭错误只作为附加信息。
                ex.addSuppressed(closeError);
            }
            throw ex;
        }
    }
}
