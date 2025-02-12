package cn.bcd.base.kafka.ext.threaddriven;

import cn.bcd.base.exception.BaseException;
import cn.bcd.base.kafka.KafkaUtil;
import cn.bcd.base.kafka.ext.ConsumerRebalanceLogger;
import cn.bcd.base.util.ExecutorUtil;
import cn.bcd.base.util.StringUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 此类要求提供 kafka-client即可、不依赖spring-kafka
 * 采用如下逻辑模型
 * - 消费线程
 * - 阻塞队列
 * - 工作线程
 * <p>
 * 此类会产生如下线程
 * <p>
 * 消费者线程可能有多个、开头为 {name}-consumer
 * 例如test-consumer(1/3)-partition(0)
 * consumer(1/3)代表有3个消费线程、这是第一个
 * partition(0)代表这个消费线程消费哪个分区
 * <p>
 * 工作任务执行器线程可能有多个、开头为 {name}-worker
 * 例如test-worker(1/3)
 * worker(1/3)代表有3个工作线程、这是第一个
 * <p>
 * 销毁资源钩子线程只有一个、开头为 {name}-shutdown
 * 例如test-shutdown
 * <p>
 * 监控信息线程只有一个、开头为 {name}-monitor
 * 需要开启{@link #monitor_period}才会有
 * 例如test-monitor
 * <p>
 * 限速重置消费计数线程只有一个、开头为 {name}-reset
 * 需要开启{@link #maxConsumeSpeed}才会有
 * 例如test-reset
 * <p>
 */
public abstract class ThreadDrivenKafkaConsumer {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    public final String name;
    /**
     * 消费线程
     */
    public Thread consumeThread;
    public Thread[] consumeThreads;


    /**
     * 工作线程队列
     */
    public final BlockingQueue<ConsumerRecord<String, byte[]>>[] queues;
    public final BlockingQueue<ConsumerRecord<String, byte[]>> queue;
    /**
     * 工作线程数量
     */
    public final int workThreadNum;
    /**
     * 工作线程池队列大小
     */
    public final int workThreadQueueSize;
    /**
     * 工作线程数组
     */
    public final Thread[] workThreads;

    public final boolean oneWorkThreadOneQueue;

    /**
     * 最大阻塞(0代表不阻塞)
     */
    public final int maxBlockingNum;

    /**
     * 当前阻塞数量
     */
    public final LongAdder blockingNum = new LongAdder();

    /**
     * 是否自动释放阻塞、适用于工作内容为同步处理的逻辑
     */
    public final boolean autoReleaseBlocking;

    /**
     * 最大消费速度每秒(0代表不限制)、kafka一次消费一批数据、设置过小会导致不起作用、此时会每秒处理一批数据
     */
    public final int maxConsumeSpeed;
    public final AtomicInteger consumeCount;
    public ScheduledExecutorService resetConsumeCountPool;

    /**
     * 消费topic
     */
    public final String topic;

    /**
     * 消费topic的partition
     */
    public final int[] partitions;

    /**
     * 消费模式、由{@link #partitions}生成
     * 1、启动一个线程消费topic所有的分区
     * 2、获取topic所有的分区、每个分区启动一个线程消费指定分区
     * 3、依据{@link #partitions}、启动对应个数的线程消费指定的分区
     */
    public final int consumeMode;

    /**
     * 当前消费者是否可用
     */
    public volatile boolean available;

    /**
     * 控制退出线程标志
     */
    public volatile boolean running_consume = true;
    public volatile boolean running_work = true;

    /**
     * 监控信息
     */
    public final int monitor_period;
    public final LongAdder monitor_consumeCount;
    public final LongAdder monitor_workCount;
    public ScheduledExecutorService monitor_pool;
    private Thread shutdownHookThread;

    /**
     * @param name                  当前消费者的名称(用于标定线程名称)
     * @param oneWorkThreadOneQueue 一个工作线程一个队列
     *                              true时候
     *                              每个工作线程都有自己的队列
     *                              消费时候会根据 {@link #index(ConsumerRecord)} 将消息定位到指定线程、然后提交到对应线程的队列中
     *                              实现记录关联work线程、这在某些场景可以避免线程竞争
     *                              false时候 共享一个队列
     * @param workThreadNum         工作线程个数
     * @param workThreadQueueSize   工作线程队列大小
     *                              <=0代表不限制、此时使用{@link LinkedBlockingQueue}
     *                              其他情况、则使用{@link ArrayBlockingQueue}
     * @param maxBlockingNum        最大阻塞数量、当内存中达到最大阻塞数量时候、消费者会停止消费
     * @param autoReleaseBlocking   是否自动释放阻塞、适用于工作内容为同步处理的逻辑
     * @param maxConsumeSpeed       最大消费速度每秒(0代表不限制)、kafka一次消费一批数据、设置过小会导致不起作用、此时会每秒处理一批数据
     *                              每消费一次的数据量大小取决于如下消费者参数
     *                              {@link ConsumerConfig#MAX_POLL_RECORDS_CONFIG} 一次poll消费最大数据量
     *                              {@link ConsumerConfig#MAX_PARTITION_FETCH_BYTES_CONFIG} 每个分区最大拉取字节数
     * @param monitor_period        监控信息打印周期(秒)、0则代表不打印
     * @param topic                 消费的topic
     * @param partitions            消费的topic的分区、不同的情况消费策略不一样
     *                              如果partitions为空、则会启动单线程即一个消费者使用{@link KafkaConsumer#subscribe(Pattern)}完成订阅
     *                              如果partitions不为空、且partitions[0]<0、则会首先通过{@link KafkaConsumer#partitionsFor(String)}获取分区个数、然后启动对应的消费线程、每一个线程一个消费者使用{@link KafkaConsumer#assign(Collection)}完成分配
     *                              其他情况、则根据指定分区个数启动对应个数的线程、每个线程负责消费一个分区
     */
    public ThreadDrivenKafkaConsumer(String name,
                                     boolean oneWorkThreadOneQueue,
                                     int workThreadNum,
                                     int workThreadQueueSize,
                                     int maxBlockingNum,
                                     boolean autoReleaseBlocking,
                                     int maxConsumeSpeed,
                                     int monitor_period,
                                     String topic,
                                     int... partitions) {
        this.name = name;
        this.oneWorkThreadOneQueue = oneWorkThreadOneQueue;
        this.workThreadNum = workThreadNum;
        this.workThreadQueueSize = workThreadQueueSize;
        this.maxBlockingNum = maxBlockingNum;
        this.autoReleaseBlocking = autoReleaseBlocking;
        this.maxConsumeSpeed = maxConsumeSpeed;
        this.monitor_period = monitor_period;
        this.topic = topic;
        this.partitions = partitions;
        if (partitions.length == 0) {
            consumeMode = 1;
        } else {
            if (partitions[0] < 0) {
                consumeMode = 2;
            } else {
                consumeMode = 3;
            }
        }

        if (monitor_period == 0) {
            monitor_consumeCount = null;
            monitor_workCount = null;
        } else {
            monitor_consumeCount = new LongAdder();
            monitor_workCount = new LongAdder();
        }

        if (maxConsumeSpeed > 0) {
            consumeCount = new AtomicInteger();
        } else {
            consumeCount = null;
        }

        //初始化工作线程
        this.workThreads = new Thread[workThreadNum];
        //是否一个工作线程一个队列
        if (oneWorkThreadOneQueue) {
            this.queue = null;
            this.queues = new BlockingQueue[workThreadNum];
            for (int i = 0; i < workThreadNum; i++) {
                final BlockingQueue<ConsumerRecord<String, byte[]>> queue;
                if (workThreadQueueSize <= 0) {
                    queue = new LinkedBlockingQueue<>();
                } else {
                    queue = new ArrayBlockingQueue<>(workThreadQueueSize);
                }
                this.queues[i] = queue;
                workThreads[i] = new Thread(() -> work(queue), name + "-worker" + "(" + (i + 1) + "/" + workThreadNum + ")");
            }
        } else {
            this.queues = null;
            final BlockingQueue<ConsumerRecord<String, byte[]>> queue;
            if (workThreadQueueSize <= 0) {
                queue = new LinkedBlockingQueue<>();
            } else {
                queue = new ArrayBlockingQueue<>(workThreadQueueSize);
            }
            this.queue = queue;
            for (int i = 0; i < workThreadNum; i++) {
                workThreads[i] = new Thread(() -> work(queue), name + "-worker" + "(" + (i + 1) + "/" + workThreadNum + ")");
            }
        }

    }

    private void startConsumePartitions(KafkaConsumer<String, byte[]> consumer, int[] ps, KafkaProperties.Consumer consumerProp) {
        if (ps.length == 0) {
            consumer.close();
        } else {
            int partitionSize = ps.length;
            KafkaConsumer<String, byte[]>[] consumers = new KafkaConsumer[partitionSize];
            try {
                int firstPartition = ps[0];
                consumer.assign(Collections.singletonList(new TopicPartition(topic, firstPartition)));
                consumers[0] = consumer;
                for (int i = 1; i < partitionSize; i++) {
                    int partition = ps[i];
                    final KafkaConsumer<String, byte[]> cur = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                    cur.assign(Collections.singletonList(new TopicPartition(topic, partition)));
                    consumers[i] = cur;
                }
            } catch (Exception ex) {
                //发生异常则关闭之前构造的消费者
                for (KafkaConsumer<String, byte[]> cur : consumers) {
                    if (cur != null) {
                        cur.close();
                    }
                }
                throw cn.bcd.base.exception.BaseException.get(ex);
            }
            consumeThreads = new Thread[partitionSize];
            for (int i = 0; i < partitionSize; i++) {
                final KafkaConsumer<String, byte[]> cur = consumers[i];
                Thread thread = new Thread(() -> consume(cur), name + "-consumer(" + (i + 1) + "/" + partitionSize + ")-partition(" + i + ")");
                consumeThreads[i] = thread;
                thread.start();
            }
        }
        logger.info("start consumers[{}] for partitions[{}]", partitions.length, Arrays.stream(partitions).mapToObj(e -> topic + ":" + e).collect(Collectors.joining(",")));
    }

    /**
     * 初始化
     * @param consumerProp
     */
    public void init(KafkaProperties.Consumer consumerProp) {
        if (!available) {
            synchronized (this) {
                if (!available) {
                    try {
                        //标记可用
                        available = true;
                        //初始化重置消费计数线程池(如果有限制最大消费速度)、提交工作任务、每秒重置消费数量
                        if (maxConsumeSpeed > 0) {
                            resetConsumeCountPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-reset"));
                            resetConsumeCountPool.scheduleAtFixedRate(() -> {
                                consumeCount.set(0);
                            }, 1, 1, TimeUnit.SECONDS);
                        }
                        //初始化工作线程池、提交工作任务
                        for (int i = 0; i < workThreadNum; i++) {
                            workThreads[i].start();
                        }
                        //启动监控
                        if (monitor_period != 0) {
                            monitor_pool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-monitor"));
                            monitor_pool.scheduleAtFixedRate(() -> logger.info(monitor_log()), monitor_period, monitor_period, TimeUnit.SECONDS);
                        }
                        //启动消费者
                        switch (consumeMode) {
                            case 1: {
                                final KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                                consumer.subscribe(Collections.singletonList(topic), new ConsumerRebalanceLogger(consumer));
                                //初始化消费线程、提交消费任务
                                consumeThread = new Thread(() -> consume(consumer), name + "-consumer(1/1)-partition(all)");
                                consumeThread.start();
                                logger.info("start consumer for topic[{}]", topic);
                                break;
                            }
                            case 2: {
                                final KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                                int[] ps = consumer.partitionsFor(topic).stream().mapToInt(PartitionInfo::partition).toArray();
                                startConsumePartitions(consumer, ps, consumerProp);
                                break;
                            }
                            default: {
                                final KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(consumerProp);
                                startConsumePartitions(consumer, partitions, consumerProp);
                                break;
                            }
                        }
                        //增加销毁回调
                        shutdownHookThread = new Thread(this::destroy, name + "-shutdown");
                        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
                    } catch (Exception ex) {
                        //初始化异常、则销毁资源
                        destroy();
                        throw BaseException.get(ex);
                    }
                }
            }
        }
    }


    public void destroy() {
        if (available) {
            synchronized (this) {
                if (available) {
                    //打上退出标记、等待消费线程退出
                    running_consume = false;
                    ExecutorUtil.shutdownThenAwait(consumeThread, consumeThreads, resetConsumeCountPool, queue, queues);
                    //打上退出标记、等待工作线程退出
                    running_work = false;
                    ExecutorUtil.shutdownThenAwait(workThreads, monitor_pool);
                    //取消shutdownHook
                    if (shutdownHookThread != null) {
                        try {
                            Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
                        } catch (IllegalStateException ex) {
                            throw BaseException.get(ex);
                        }
                    }
                    //标记不可用
                    available = false;

                }
            }
        }
    }

    /**
     * 当{@link #oneWorkThreadOneQueue}为true时候生效
     * 可以根据数据内容绑定到对应的工作线程上、避免线程竞争
     *
     * @param consumerRecord
     * @return [0-{@link #workThreadNum})中某个值
     */
    protected int index(ConsumerRecord<String, byte[]> consumerRecord) {
        return Math.floorMod(consumerRecord.key().hashCode(), workThreadNum);
    }

    /**
     * 消费
     */
    private void consume(KafkaConsumer<String, byte[]> consumer) {
        try {
            if (oneWorkThreadOneQueue) {
                while (running_consume) {
                    try {
                        //检查阻塞
                        if (blockingNum.sum() >= maxBlockingNum) {
                            TimeUnit.MILLISECONDS.sleep(100);
                            continue;
                        }
                        //消费一批数据
                        final ConsumerRecords<String, byte[]> consumerRecords = consumer.poll(Duration.ofSeconds(3));
                        if (consumerRecords == null || consumerRecords.isEmpty()) {
                            continue;
                        }

                        //统计
                        final int count = consumerRecords.count();
                        blockingNum.add(count);
                        if (monitor_period > 0) {
                            monitor_consumeCount.add(count);
                        }

                        //检查速度、如果速度太快则阻塞
                        if (maxConsumeSpeed > 0) {
                            //控制每秒消费、如果消费过快、则阻塞一会、放慢速度
                            final int curConsumeCount = consumeCount.addAndGet(count);
                            if (curConsumeCount >= maxConsumeSpeed) {
                                do {
                                    TimeUnit.MILLISECONDS.sleep(50);
                                } while (consumeCount.get() >= maxConsumeSpeed);
                            }
                        }
                        //发布消息
                        for (ConsumerRecord<String, byte[]> consumerRecord : consumerRecords) {
                            //放入队列
                            queues[index(consumerRecord)].put(consumerRecord);
                        }
                    } catch (Exception ex) {
                        logger.error("kafka consumer topic[{}] cycle error,try again after 3s", topic, ex);
                        try {
                            TimeUnit.SECONDS.sleep(3);
                        } catch (InterruptedException e) {
                            throw BaseException.get(e);
                        }
                    }
                }
            } else {
                while (running_consume) {
                    try {
                        //检查阻塞
                        if (blockingNum.sum() >= maxBlockingNum) {
                            TimeUnit.MILLISECONDS.sleep(100);
                            continue;
                        }
                        //消费一批数据
                        final ConsumerRecords<String, byte[]> consumerRecords = consumer.poll(Duration.ofSeconds(3));

                        if (consumerRecords == null || consumerRecords.isEmpty()) {
                            continue;
                        }

                        //统计
                        final int count = consumerRecords.count();
                        blockingNum.add(count);
                        if (monitor_period > 0) {
                            monitor_consumeCount.add(count);
                        }

                        //检查速度、如果速度太快则阻塞
                        if (maxConsumeSpeed > 0) {
                            //控制每秒消费、如果消费过快、则阻塞一会、放慢速度
                            final int curConsumeCount = consumeCount.addAndGet(count);
                            if (curConsumeCount >= maxConsumeSpeed) {
                                do {
                                    TimeUnit.MILLISECONDS.sleep(10);
                                } while (consumeCount.get() >= maxConsumeSpeed);
                            }
                        }
                        //发布消息
                        for (ConsumerRecord<String, byte[]> consumerRecord : consumerRecords) {
                            //放入队列
                            queue.put(consumerRecord);
                        }
                    } catch (Exception ex) {
                        logger.error("kafka consumer topic[{}] cycle error,try again after 3s", topic, ex);
                        try {
                            TimeUnit.SECONDS.sleep(3);
                        } catch (InterruptedException e) {
                            throw BaseException.get(e);
                        }
                    }
                }
            }
        } finally {
            String assignment = consumer.assignment().stream().map(e -> e.topic() + ":" + e.partition()).collect(Collectors.joining(","));
            logger.info("consumer[{}] assignment[{}] close", this.getClass().getName(), assignment);
            consumer.close();
        }

    }

    /**
     * 工作线程
     *
     * @param queue
     */
    private void work(final BlockingQueue<ConsumerRecord<String, byte[]>> queue) {
        try {
            while (running_work) {
                final ConsumerRecord<String, byte[]> poll = queue.poll(1, TimeUnit.SECONDS);
                if (poll != null) {
                    try {
                        onMessage(poll);
                    } catch (Exception ex) {
                        logger.error("onMessage error", ex);
                    }
                    if (monitor_period > 0) {
                        monitor_workCount.increment();
                    }
                    if (autoReleaseBlocking) {
                        blockingNum.decrement();
                    }
                }
            }
        } catch (InterruptedException ex) {
            throw BaseException.get(ex);
        }
    }


    public abstract void onMessage(ConsumerRecord<String, byte[]> consumerRecord) throws Exception;


    private String getQueueLog(BlockingQueue<ConsumerRecord<String, byte[]>> queue) {
        return queue.size() + (workThreadQueueSize > 0 ? ("/" + workThreadQueueSize) : "");
    }

    /**
     * 监控日志
     * 如果需要修改日志、可以重写此方法
     * blocking 当前阻塞数量/最大阻塞数量
     * consume 周期内所有的消费者合计消费数量
     * queues 所有执行器的队列情况、每个执行器 当前队列大小/最大队列大小(如果有最大队列大小)
     * work 周期内所有的任务执行器合计处理数量
     */
    public String monitor_log() {
        return StringUtil.format("name[{}] blocking[{}/{}] consume[{}/{}s] queues[{}] work[{}/{}s]",
                name,
                blockingNum.sum(), maxBlockingNum,
                monitor_consumeCount.sumThenReset(), monitor_period,
                oneWorkThreadOneQueue ? Arrays.stream(queues).map(this::getQueueLog).collect(Collectors.joining(",")) : getQueueLog(queue),
                monitor_workCount.sumThenReset(), monitor_period);
    }
}

