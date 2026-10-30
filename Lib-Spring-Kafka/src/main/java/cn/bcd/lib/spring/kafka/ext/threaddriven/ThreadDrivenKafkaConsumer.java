package cn.bcd.lib.spring.kafka.ext.threaddriven;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.StringUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.spring.kafka.ext.KafkaConsumerGroup;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * 按业务 ID 将消息路由到固定的单线程执行器，由 onMessage 处理。
 * 相同 ID 的回调串行执行，回调可通过传入的执行器提交后续任务。
 */
public abstract class ThreadDrivenKafkaConsumer implements AutoCloseable {
    protected Logger logger = LoggerFactory.getLogger(getClass());

    public final String name;
    public final int maxBlockingNum;
    public final boolean autoReleaseBlocking;
    public final int maxConsumeSpeed;
    public final int monitor_period;
    public final ConsumerParam consumerParam;

    public final LongAdder blockingNum = new LongAdder();
    public final AtomicInteger consumeCount = new AtomicInteger();
    public final LongAdder monitor_consumeCount = new LongAdder();
    public final LongAdder monitor_workCount = new LongAdder();
    public final ScheduledExecutorService scheduledPool;
    public KafkaConsumerGroup consumerGroup;

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration PAUSED_POLL_TIMEOUT = Duration.ofMillis(100);
    private CompletableFuture<Void> startupResult;
    private CompletableFuture<Void> closeResult;
    private boolean closed;
    volatile boolean running_consume;
    private volatile boolean pauseConsume;
    private volatile Thread schedulerThread;

    public final int workThreadNum;
    public final SingleThreadEventExecutor[] workPool;

    /**
     * @param name 消费者名称，用于线程命名
     * @param workThreadNum 工作执行器数量，向上取整为 2 的幂
     * @param maxBlockingNum 最大消息积压，0 表示不限制；按批检查，可能超出一批消息
     * @param autoReleaseBlocking 同步处理时设为 true；false 时由业务在异步完成后释放 blockingNum
     * @param maxConsumeSpeed 每秒最大消息数，0 表示不限制；按 poll 批次限速
     * @param monitor_period 监控日志周期（秒），0 表示关闭
     * @param consumerParam 订阅、分区和起始位置配置
     */
    public ThreadDrivenKafkaConsumer(String name, int workThreadNum, int maxBlockingNum,
                                     boolean autoReleaseBlocking, int maxConsumeSpeed,
                                     int monitor_period, ConsumerParam consumerParam) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (workThreadNum <= 0 || workThreadNum > (1 << 30)) {
            throw new IllegalArgumentException("worker count must be between 1 and 2^30");
        }
        if (maxBlockingNum < 0 || maxConsumeSpeed < 0 || monitor_period < 0) {
            throw new IllegalArgumentException("blocking limit, consume speed and monitor period cannot be negative");
        }
        this.name = name;
        this.maxBlockingNum = maxBlockingNum;
        this.autoReleaseBlocking = autoReleaseBlocking;
        this.maxConsumeSpeed = maxConsumeSpeed;
        this.monitor_period = monitor_period;
        this.consumerParam = Objects.requireNonNull(consumerParam, "consumerParam");
        scheduledPool = maxConsumeSpeed == 0 && monitor_period == 0 ? null
                : Executors.newSingleThreadScheduledExecutor(task -> {
                    schedulerThread = new Thread(task, name + "-schedule");
                    return schedulerThread;
                });
        this.workThreadNum = workerCount(workThreadNum);
        workPool = new SingleThreadEventExecutor[this.workThreadNum];
        try {
            for (int i = 0; i < workPool.length; i++) {
                String workerName = name + "-worker(" + (i + 1) + "/" + workPool.length + ")";
                workPool[i] = new DefaultEventExecutor((ThreadFactory) task -> new Thread(task, workerName));
            }
            startMaintenance();
        } catch (Exception ex) {
            closeInternal();
            throw BaseException.get(ex);
        }
    }

    /** 工作执行器创建完成后再启动调度任务。 */
    private void startMaintenance() {
        if (maxConsumeSpeed > 0) {
            scheduledPool.scheduleAtFixedRate(() -> consumeCount.set(0), 1, 1, TimeUnit.SECONDS);
        }
        if (monitor_period > 0) {
            scheduledPool.scheduleAtFixedRate(() -> logger.info(monitor_log()),
                    monitor_period, monitor_period, TimeUnit.SECONDS);
        }
    }

    /**
     * 返回全部消费者的启动结果，包括创建、订阅/分区指定和 seek 初始化。
     * 默认 subscribe 模式的分区分配在后续 poll 中发生；此 Future 不代表消费结束。
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

    public final void pauseConsume() {
        pauseConsume = true;
    }

    public final void resumeConsume() {
        pauseConsume = false;
    }

    /** 在所属 Kafka 消费线程运行，原生消费者由 KafkaConsumerGroup 关闭。 */
    public void consume(KafkaConsumer<String, byte[]> consumer) {
        try {
            while (running_consume) {
                try {
                    pollAndDispatch(consumer);
                } catch (Exception ex) {
                    if (!running_consume || !retryAfterFailure(ex)) {
                        break;
                    }
                }
            }
        } finally {
            running_consume = false;
        }
    }

    private void pollAndDispatch(KafkaConsumer<String, byte[]> consumer) {
        boolean throttled = isThrottled();
        if (throttled) {
            consumer.pause(consumer.assignment());
        } else {
            var paused = consumer.paused();
            if (!paused.isEmpty()) {
                consumer.resume(paused);
            }
        }
        // 暂停拉取时继续 poll，避免超过 max.poll.interval.ms。
        ConsumerRecords<String, byte[]> records = consumer.poll(throttled ? PAUSED_POLL_TIMEOUT : POLL_TIMEOUT);
        if (maxConsumeSpeed > 0) {
            consumeCount.addAndGet(records.count());
        }
        for (ConsumerRecord<String, byte[]> record : records) {
            if (!running_consume) {
                break;
            }
            submitRecord(record);
        }
    }

    private boolean isThrottled() {
        return pauseConsume
                || (maxBlockingNum > 0 && blockingNum.sum() >= maxBlockingNum)
                || (maxConsumeSpeed > 0 && consumeCount.get() >= maxConsumeSpeed);
    }

    private boolean retryAfterFailure(Exception error) {
        logger.error("kafka consumer cycle error,try again after 3s", error);
        try {
            TimeUnit.SECONDS.sleep(3);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    protected String id(ConsumerRecord<String, byte[]> record) {
        return record.key();
    }

    private static int workerCount(int requested) {
        int n = -1 >>> Integer.numberOfLeadingZeros(requested - 1);
        return n < 0 ? 1 : n + 1;
    }

    /** 相同 ID 使用同一执行器，空 ID 使用第一个；子类可重写路由。 */
    public SingleThreadEventExecutor getWorkExecutor(String id) {
        if (id == null) {
            return workPool[0];
        }
        int hash = id.hashCode();
        return workPool[(hash ^ (hash >>> 16)) & (workThreadNum - 1)];
    }

    protected final void submitRecord(ConsumerRecord<String, byte[]> record) {
        SingleThreadEventExecutor executor = getWorkExecutor(id(record));
        blockingNum.increment();
        try {
            executor.submit(() -> work(record, executor));
        } catch (RuntimeException | Error ex) {
            blockingNum.decrement();
            throw ex;
        }
        if (monitor_period > 0) {
            monitor_consumeCount.increment();
        }
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

    /** executor 是当前执行回调的执行器，可向其提交后续任务。 */
    public abstract void onMessage(ConsumerRecord<String, byte[]> record,
                                   SingleThreadEventExecutor executor) throws Exception;

    public String monitor_log() {
        return StringUtil.format("name[{}] blocking[{}/{}] consume[{}/{}s] workPool[{}] work[{}/{}s]",
                name, blockingNum.sum(), maxBlockingNum,
                monitor_consumeCount.sumThenReset(), monitor_period,
                Arrays.stream(workPool).map(executor -> Integer.toString(executor.pendingTasks()))
                        .collect(Collectors.joining(",")),
                monitor_workCount.sumThenReset(), monitor_period);
    }

    /** 内部线程只发起关闭，外部线程等待同一次关闭完成。 */
    @Override
    public void close() {
        boolean ownedThread = isCurrentOwnedThread();
        CompletableFuture<Void> result;
        synchronized (this) {
            if (!closed) {
                closed = true;
                closeResult = CompletableFuture.runAsync(this::closeResources,
                        task -> new Thread(task, name + "-shutdown").start());
            }
            result = closeResult;
        }
        if (!ownedThread) {
            result.join();
        }
    }

    private void closeResources() {
        try {
            closeInternal();
        } catch (RuntimeException | Error ex) {
            logger.error("kafka consumer close error", ex);
            throw ex;
        }
    }

    /** 也用于构造失败后的资源清理。 */
    private void closeInternal() {
        running_consume = false;
        if (consumerGroup != null) {
            consumerGroup.close();
        }
        ExecutorUtil.shutdownThenAwait(true, scheduledPool);
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
        if (Thread.currentThread() == schedulerThread
                || (consumerGroup != null && consumerGroup.contains(Thread.currentThread()))) {
            return true;
        }
        for (SingleThreadEventExecutor executor : workPool) {
            if (executor != null && executor.inEventLoop()) {
                return true;
            }
        }
        return false;
    }
}

