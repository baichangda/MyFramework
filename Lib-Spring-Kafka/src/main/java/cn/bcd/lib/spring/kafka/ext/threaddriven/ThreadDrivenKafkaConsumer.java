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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Kafka 消费线程拉取消息，按业务 ID 提交到对应的单线程工作执行器。
 * 限速重置和监控共用独立调度线程；工作回调可以通过传入的执行器提交后续任务。
 * 关闭时先停止消费，再等待工作任务完成；从内部线程关闭时转交独立线程处理。
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
    private CompletableFuture<Void> closeResult;


    /**
     * 工作执行器数组
     */
    public final SingleThreadEventExecutor[] workPool;


    /**
     * 重置消费计数
     */
    public final AtomicInteger consumeCount = new AtomicInteger();
    public final ScheduledExecutorService scheduledPool;

    /**
     * 监控信息
     */
    public final LongAdder monitor_consumeCount = new LongAdder();
    public final LongAdder monitor_workCount = new LongAdder();

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
    volatile boolean pause_consume;


    /**
     * @param name                  当前消费者的名称(用于标定线程名称、消费组id)
     * @param workThreadNum         工作执行器数量，向上取整为2的幂
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
        workPool = new SingleThreadEventExecutor[this.workThreadNum];
        scheduledPool = maxConsumeSpeed == 0 && monitor_period == 0 ? null
                : Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-schedule"));
        try {
            for (int i = 0; i < this.workThreadNum; i++) {
                final String workerName = name + "-worker(" + (i + 1) + "/" + this.workThreadNum + ")";
                workPool[i] = new DefaultEventExecutor((ThreadFactory) r -> new Thread(r, workerName));
            }
            if (maxConsumeSpeed != 0) {
                scheduledPool.scheduleAtFixedRate(() -> consumeCount.set(0), 1, 1, TimeUnit.SECONDS);
            }
            if (monitor_period != 0) {
                scheduledPool.scheduleAtFixedRate(() -> logger.info(monitor_log()),
                        monitor_period, monitor_period, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            closeInternal();
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
        if (running_consume) {
            return startupResult;
        }
        running_consume = true;
        try {
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


    /**
     * 所属工作或消费线程只发起关闭；其他线程始终等待同一次关闭完成，包括重复调用。
     */
    @Override
    public void close() {
        boolean async = isCurrentOwnedThread();
        CompletableFuture<Void> result;
        synchronized (this) {
            if (!closed) {
                closed = true;
                closeResult = CompletableFuture.runAsync(this::closeInternal,
                        task -> new Thread(task, name + "-shutdown").start())
                        .whenComplete((ignored, error) -> {
                            if (error != null) {
                                logger.error("kafka consumer close error", error);
                            }
                        });
            }
            result = closeResult;
        }
        if (!async) {
            result.join();
        }
    }

    private void closeInternal() {
        running_consume = false;
        if (consumerGroup != null) {
            consumerGroup.close();
        }
        ExecutorUtil.shutdownThenAwait(true, scheduledPool);
        // 先通知全部执行器关闭，再等待已提交的任务执行完毕。
        for (SingleThreadEventExecutor executor : workPool) {
            if (executor != null) {
                executor.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            }
        }
        for (SingleThreadEventExecutor executor : workPool) {
            if (executor != null) {
                executor.terminationFuture().syncUninterruptibly();
            }
        }
    }

    private boolean isCurrentOwnedThread() {
        return Arrays.stream(workPool).anyMatch(executor -> executor.inEventLoop())
                || (consumerGroup != null && consumerGroup.contains(Thread.currentThread()));
    }

    /**
     * 消费
     */
    private void consume(KafkaConsumer<String, byte[]> consumer) {
        try {
            while (running_consume) {
                try {
                    boolean throttled = pause_consume
                            || (maxBlockingNum > 0 && blockingNum.sum() >= maxBlockingNum)
                            || (maxConsumeSpeed > 0 && consumeCount.get() >= maxConsumeSpeed);
                    if (throttled) {
                        consumer.pause(consumer.assignment());
                    } else {
                        consumer.resume(consumer.paused());
                    }
                    // 暂停拉取时仍需 poll，维持消费组成员身份。
                    ConsumerRecords<String, byte[]> consumerRecords = consumer.poll(
                            throttled ? Duration.ofMillis(100) : Duration.ofSeconds(3));
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
                    if (!running_consume) {
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
        }
    }

    /**
     * 获取消息的业务 ID，默认使用消息 key，子类可重写。
     */
    protected String id(ConsumerRecord<String, byte[]> consumerRecord) {
        return consumerRecord.key();
    }

    /**
     * 根据 ID 选择工作执行器，相同 ID 始终映射到同一个执行器，空 ID 使用第一个执行器。
     */
    public SingleThreadEventExecutor getWorkExecutor(String id) {
        if (id == null) {
            return workPool[0];
        }
        int h = id.hashCode();
        h = h ^ (h >>> 16);
        return workPool[h & (workThreadNum - 1)];
    }

    protected final void submitRecord(ConsumerRecord<String, byte[]> record) {
        SingleThreadEventExecutor executor = getWorkExecutor(id(record));
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

