package cn.bcd.lib.base.kafka.ext.threaddriven;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.kafka.ext.KafkaExtUtil;
import cn.bcd.lib.base.kafka.ext.PartitionMode;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.base.util.StringUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
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
    public final boolean oneWorkThreadOneQueue;
    public final int workThreadNum;
    public final int workThreadQueueSize;
    public final int maxBlockingNum;
    public final boolean autoReleaseBlocking;
    public final int maxConsumeSpeed;
    public final int monitor_period;
    public final String topic;
    public final PartitionMode partitionMode;

    /**
     * 当前阻塞数量
     */
    public final LongAdder blockingNum = new LongAdder();

    /**
     * 消费线程
     */
    public Thread consumeThread;
    public Thread[] consumeThreads;


    /**
     * 工作线程队列
     */
    public BlockingQueue<ConsumerRecord<String, byte[]>> queue;
    public BlockingQueue<ConsumerRecord<String, byte[]>>[] queues;


    /**
     * 工作线程数组
     */
    public Thread[] workThreads;


    /**
     * 重置消费计数
     */
    public AtomicInteger consumeCount;
    public ScheduledExecutorService resetConsumeCountPool;

    /**
     * 监控信息
     */
    public LongAdder monitor_consumeCount;
    public LongAdder monitor_workCount;
    public ScheduledExecutorService monitor_pool;


    /**
     * 当前消费者是否运行中
     */
    volatile boolean running;

    /**
     * 控制退出线程标志
     */
    volatile boolean running_consume;
    volatile boolean running_work;


    /**
     * @param name                  当前消费者的名称(用于标定线程名称、消费组id)
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
     * @param partitionMode         null则代表PartitionMode.get(0)、即启动单线程、一个消费者、使用{@link KafkaConsumer#subscribe(Pattern)}完成订阅这个topic的所有分区\
     *                              其他情况参考{@link PartitionMode#mode}
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
                                     PartitionMode partitionMode) {
        this.name = name;
        this.oneWorkThreadOneQueue = oneWorkThreadOneQueue;
        this.workThreadNum = workThreadNum;
        this.workThreadQueueSize = workThreadQueueSize;
        this.maxBlockingNum = maxBlockingNum;
        this.autoReleaseBlocking = autoReleaseBlocking;
        this.maxConsumeSpeed = maxConsumeSpeed;
        this.monitor_period = monitor_period;
        this.topic = topic;
        if (partitionMode == null) {
            this.partitionMode = PartitionMode.get(0);
        } else {
            this.partitionMode = partitionMode;
        }
    }

    /**
     * 初始化
     *
     * @param consumerProp
     */
    @SuppressWarnings("unchecked")
    public synchronized void init(Map<String, Object> consumerProp) {
        if (!running) {
            try {
                //标记可用
                running = true;
                running_consume = true;
                running_work = true;

                //初始化重置消费计数线程池(如果有限制最大消费速度)、提交工作任务、每秒重置消费数量
                if (maxConsumeSpeed > 0) {
                    consumeCount = new AtomicInteger();
                    resetConsumeCountPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-reset"));
                    resetConsumeCountPool.scheduleAtFixedRate(() -> {
                        consumeCount.set(0);
                    }, 1, 1, TimeUnit.SECONDS);
                }

                //初始化工作队列、工作线程池
                workThreads = new Thread[workThreadNum];
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
                        workThreads[i].start();
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
                        workThreads[i].start();
                    }
                }

                //启动监控
                if (monitor_period != 0) {
                    monitor_consumeCount = new LongAdder();
                    monitor_workCount = new LongAdder();
                    monitor_pool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-monitor"));
                    monitor_pool.scheduleAtFixedRate(() -> logger.info(monitor_log()), monitor_period, monitor_period, TimeUnit.SECONDS);
                }
                //启动消费者
                KafkaExtUtil.ConsumerThreadHolder holder = KafkaExtUtil.startConsumer(name, topic, consumerProp, partitionMode, this::consume);
                consumeThread = holder.thread();
                consumeThreads = holder.threads();
            } catch (Exception ex) {
                //初始化异常、则销毁资源
                destroy();
                throw BaseException.get(ex);
            }
        }
    }


    public synchronized void destroy() {
        if (running) {
            //打上退出标记、等待消费线程退出
            running_consume = false;
            ExecutorUtil.shutdownThenAwait(consumeThread, consumeThreads, resetConsumeCountPool, queue, queues);
            //打上退出标记、等待工作线程退出
            running_work = false;
            ExecutorUtil.shutdownThenAwait(workThreads, monitor_pool);
            //标记不可用
            running = false;
            //清空变量
            blockingNum.reset();
            consumeThread = null;
            consumeThreads = null;
            queue = null;
            queues = null;
            workThreads = null;
            consumeCount = null;
            resetConsumeCountPool = null;
            monitor_consumeCount = null;
            monitor_workCount = null;
            monitor_pool = null;
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
        if (workThreadNum == 1) {
            return 0;
        } else {
            return Math.floorMod(consumerRecord.key().hashCode(), workThreadNum);
        }
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
                        final ConsumerRecords<String, byte[]> consumerRecords = consumer.poll(Duration.ofSeconds(1));
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

