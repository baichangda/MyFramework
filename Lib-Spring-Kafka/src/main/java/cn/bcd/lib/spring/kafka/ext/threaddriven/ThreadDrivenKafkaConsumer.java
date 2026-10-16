package cn.bcd.lib.spring.kafka.ext.threaddriven;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.base.util.StringUtil;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.KafkaConsumerGroup;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * 此类要求提供 kafka-client即可、不依赖spring-kafka
 * 采用如下逻辑模型
 * - 消费线程
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
public abstract class ThreadDrivenKafkaConsumer implements AutoCloseable {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    public final String name;
    public final int workThreadNum;
    public final int maxBlockingNum;
    public final boolean autoReleaseBlocking;
    public final int maxConsumeSpeed;
    public final int monitor_period;
    public final ConsumerParam consumerParam;

    /**
     * 当前阻塞数量
     */
    public final LongAdder blockingNum = new LongAdder();

    /**
     * 消费线程
     */
    public KafkaConsumerGroup consumerGroup;
    private CompletableFuture<Void> startupResult;


    /**
     * 工作执行器数组
     */
    public final SingleThreadEventExecutor[] workPool;
    private final AtomicInteger nextWorkerIndex = new AtomicInteger();


    /**
     * 重置消费计数
     */
    public final AtomicInteger consumeCount;
    public final ScheduledExecutorService resetConsumeCountPool;

    /**
     * 监控信息
     */
    public final LongAdder monitor_consumeCount;
    public final LongAdder monitor_workCount;
    public final ScheduledExecutorService monitor_pool;

    /**
     * 是否关闭
     */
    boolean closed;

    /**
     * 控制退出线程标志
     */
    volatile boolean running_consume;

    /**
     * 是否暂停消费
     */
    volatile boolean pause_consume = false;


    /**
     * @param name                  当前消费者的名称(用于标定线程名称、消费组id)
     * @param workThreadNum         工作线程个数、最好是2的倍数、如果不是向上取整到2的倍数
     * @param maxBlockingNum        最大阻塞数量、当内存中达到最大阻塞数量时候、消费者会停止消费
     * @param autoReleaseBlocking   是否自动释放阻塞、适用于工作内容为同步处理的逻辑
     * @param maxConsumeSpeed       最大消费速度每秒(0代表不限制)、kafka一次消费一批数据、设置过小会导致不起作用、此时会每秒处理一批数据
     *                              每消费一次的数据量大小取决于如下消费者参数
     *                              {@link ConsumerConfig#MAX_POLL_RECORDS_CONFIG} 一次poll消费最大数据量
     *                              {@link ConsumerConfig#MAX_PARTITION_FETCH_BYTES_CONFIG} 每个分区最大拉取字节数
     * @param monitor_period        监控信息打印周期(秒)、0则代表不打印
     * @param consumerParam         消费者的参数、不能为null
     *                              主要用于设置消费的topic、分区、消费线程、消费者开始消费的位置
     *                              具体参考{@link ConsumerParam}中静态方法
     */
    public ThreadDrivenKafkaConsumer(String name,
                                     int workThreadNum,
                                     int maxBlockingNum,
                                     boolean autoReleaseBlocking,
                                     int maxConsumeSpeed,
                                     int monitor_period,
                                     ConsumerParam consumerParam) {
        this.name = name;
        this.workThreadNum = tableSizeFor(workThreadNum);
        this.maxBlockingNum = maxBlockingNum;
        this.autoReleaseBlocking = autoReleaseBlocking;
        this.maxConsumeSpeed = maxConsumeSpeed;
        this.monitor_period = monitor_period;
        this.consumerParam = consumerParam;
        try {
            //初始化重置消费计数线程池(如果有限制最大消费速度)、提交工作任务、每秒重置消费数量
            if (maxConsumeSpeed == 0) {
                consumeCount = null;
                resetConsumeCountPool = null;
            } else {
                consumeCount = new AtomicInteger();
                resetConsumeCountPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-reset"));
                resetConsumeCountPool.scheduleAtFixedRate(() -> {
                    consumeCount.set(0);
                }, 1, 1, TimeUnit.SECONDS);
            }

            workPool = new SingleThreadEventExecutor[this.workThreadNum];
            for (int i = 0; i < this.workThreadNum; i++) {
                final String workerName = name + "-worker(" + (i + 1) + "/" + this.workThreadNum + ")";
                workPool[i] = new DefaultEventExecutor((ThreadFactory) r -> new Thread(r, workerName));
            }

            //启动监控
            if (monitor_period == 0) {
                monitor_consumeCount = null;
                monitor_workCount = null;
                monitor_pool = null;
            } else {
                monitor_consumeCount = new LongAdder();
                monitor_workCount = new LongAdder();
                monitor_pool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-monitor"));
                monitor_pool.scheduleAtFixedRate(() -> logger.info(monitor_log()), monitor_period, monitor_period, TimeUnit.SECONDS);
            }

        } catch (Exception ex) {
            //初始化异常、则销毁资源
            close();
            throw BaseException.get(ex);
        }
    }

    private static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : n + 1;
    }

    /**
     * 开始消费
     */
    public synchronized CompletableFuture<Void> startConsume(Map<String, Object> consumerProp) {
        if (closed) {
            throw new IllegalStateException("consumer already closed");
        }
        if (!running_consume) {
            running_consume = true;
            try {
                //启动消费者
                consumerGroup = KafkaConsumerGroup.create(name, consumerProp, consumerParam, this::consume);
                startupResult = consumerGroup.start().whenComplete((ignored, error) -> {
                    if (error != null) {
                        running_consume = false;
                    }
                });
            } catch (RuntimeException | Error ex) {
                running_consume = false;
                logger.error("kafka consumer start error", ex);
                startupResult = CompletableFuture.failedFuture(ex);
            }
        }
        return startupResult;
    }

    /**
     * 暂停消费
     */
    public final void pauseConsume() {
        pause_consume = true;
    }

    /**
     * 恢复消费
     */
    public final void resumeConsume() {
        pause_consume = false;
    }


    @Override
    public void close() {
        boolean async;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            async = isCurrentOwnedThread();
        }
        if (async) {
            new Thread(this::closeInternal, name + "-shutdown").start();
        } else {
            closeInternal();
        }
    }

    private void closeInternal() {
        //打上退出标记、等待消费线程退出
        running_consume = false;
        if (consumerGroup != null) {
            consumerGroup.close();
        }
        ExecutorUtil.shutdownThenAwait(true, resetConsumeCountPool);
        //关闭工作执行器，等待已提交任务执行完毕
        if (workPool != null) {
            for (SingleThreadEventExecutor executor : workPool) {
                if (executor != null) {
                    executor.shutdownGracefully(0, 5, TimeUnit.SECONDS);
                }
            }
            for (SingleThreadEventExecutor executor : workPool) {
                if (executor != null && !executor.inEventLoop()) {
                    executor.terminationFuture().syncUninterruptibly();
                }
            }
        }
        ExecutorUtil.shutdownThenAwait(true, monitor_pool);
    }

    private boolean isCurrentOwnedThread() {
        Thread current = Thread.currentThread();
        if (workPool != null) {
            for (SingleThreadEventExecutor executor : workPool) {
                if (executor != null && executor.inEventLoop(current)) {
                    return true;
                }
            }
        }
        return consumerGroup != null && consumerGroup.contains(current);
    }

    /**
     * 消费
     */
    private void consume(KafkaConsumer<String, byte[]> consumer) {
        try {
            boolean paused = false;
            while (running_consume) {
                try {
                    boolean throttled = pause_consume
                            || (maxBlockingNum > 0 && blockingNum.sum() >= maxBlockingNum)
                            || (maxConsumeSpeed > 0 && consumeCount.get() >= maxConsumeSpeed);
                    Duration pollDuration;
                    if (throttled) {
                        Set<TopicPartition> assignment = consumer.assignment();
                        if (!assignment.isEmpty()) {
                            consumer.pause(assignment);
                            paused = true;
                        }
                        // pause() only stops fetching; poll must continue to keep group membership alive.
                        pollDuration = Duration.ofMillis(100);
                    } else {
                        if (paused) {
                            consumer.resume(consumer.assignment());
                            paused = false;
                        }
                        pollDuration = Duration.ofSeconds(3);
                    }

                    ConsumerRecords<String, byte[]> consumerRecords = consumer.poll(pollDuration);
                    if (consumerRecords.isEmpty()) {
                        continue;
                    }
                    if (maxConsumeSpeed > 0) {
                        consumeCount.addAndGet(consumerRecords.count());
                    }
                    for (ConsumerRecord<String, byte[]> consumerRecord : consumerRecords) {
                        if (!running_consume) {
                            break;
                        }
                        submitRecord(consumerRecord);
                    }
                } catch (Exception ex) {
                    if (!running_consume && ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    logger.error("kafka consumer cycle error,try again after 3s", ex);
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            running_consume = false;
            String assignment = "";
            try {
                assignment = consumer.assignment().stream().map(e -> e.topic() + ":" + e.partition()).collect(Collectors.joining(","));
            } catch (Exception ex) {
                logger.debug("get consumer assignment before close error", ex);
            }
            try {
                consumer.close();
            } finally {
                logger.info("consumer[{}] assignment[{}] close", this.getClass().getName(), assignment);
            }
        }

    }

    protected final void submitRecord(ConsumerRecord<String, byte[]> record) {
        int workerIndex = nextWorkerIndex.getAndIncrement() & (workThreadNum - 1);
        SingleThreadEventExecutor executor = workPool[workerIndex];
        blockingNum.increment();
        if (monitor_period > 0) {
            monitor_consumeCount.increment();
        }
        executor.submit(() -> work(record, executor));
    }

    private void work(ConsumerRecord<String, byte[]> record, SingleThreadEventExecutor executor) {
        try {
            onMessage(record, executor);
        } catch (Exception ex) {
            logger.error("onMessage error", ex);
        } finally {
            if (monitor_period > 0) {
                monitor_workCount.increment();
            }
            if (autoReleaseBlocking) {
                blockingNum.decrement();
            }
        }
    }

    /**
     * @param consumerRecord 接收到的消息
     * @param executor 当前执行回调的执行器，可向其提交后续任务
     */
    public abstract void onMessage(ConsumerRecord<String, byte[]> consumerRecord,
                                   SingleThreadEventExecutor executor) throws Exception;

    /**
     * 监控日志
     * 如果需要修改日志、可以重写此方法
     * blocking 当前阻塞数量/最大阻塞数量
     * consume 周期内所有的消费者合计消费数量
     * workPool 各执行器等待执行的任务数量
     * work 周期内所有的任务执行器合计处理数量
     */
    public String monitor_log() {
        return StringUtil.format("name[{}] blocking[{}/{}] consume[{}/{}s] workPool[{}] work[{}/{}s]",
                name,
                blockingNum.sum(), maxBlockingNum,
                monitor_consumeCount.sumThenReset(), monitor_period,
                Arrays.stream(workPool).map(executor -> Integer.toString(executor.pendingTasks())).collect(Collectors.joining(",")),
                monitor_workCount.sumThenReset(), monitor_period);
    }
}

