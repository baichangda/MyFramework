package cn.bcd.lib.spring.kafka.ext.threaddriven;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.StringUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.ConsumerStartup;
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
 * 多个 Kafka 分区或消费线程之间不提供全局消息顺序；串行约定只覆盖进入同一执行器的任务。
 */
public abstract class ThreadDrivenKafkaConsumer implements AutoCloseable {
    protected Logger logger = LoggerFactory.getLogger(getClass());

    public final String name;
    public final int maxBlockingNum;
    public final boolean autoReleaseBlocking;
    public final int maxConsumeSpeed;
    public final int monitor_period;
    public final ConsumerParam consumerParam;

    /**
     * 尚未释放的消息数量，包含等待执行和正在处理的消息。
     * autoReleaseBlocking=false 时，业务必须在异步完成或失败后恰好释放一次；
     * 该计数只用于积压控制，不表示 Kafka offset 已提交。
     */
    public final LongAdder blockingNum = new LongAdder();
    /** 多个消费线程共用的秒级计数，由调度线程每秒归零，属于按批检查的固定窗口限速。 */
    public final AtomicInteger consumeCount = new AtomicInteger();
    /** 仅在开启监控时累计；monitor_log() 会读取并重置本周期的计数。 */
    public final LongAdder monitor_consumeCount = new LongAdder();
    /** 已执行消息回调的次数，包含回调抛异常的情况，不代表业务成功数。 */
    public final LongAdder monitor_workCount = new LongAdder();
    /** 独立于工作执行器，避免业务阻塞时连带阻塞限速重置或监控；无调度任务时为 null。 */
    public final ScheduledExecutorService scheduledPool;
    /** 本轮 Kafka 线程及启动结果的持有者，供关闭线程和内部线程识别使用。 */
    private volatile ConsumerStartup startup;

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration PAUSED_POLL_TIMEOUT = Duration.ofMillis(100);
    /** 由 synchronized 的 startConsume() 保护；启动完成后不随运行期异常重新完成。 */
    private CompletableFuture<Void> startupResult;
    /** 由 synchronized(this) 保护，所有外部关闭调用等待同一个完成结果。 */
    private CompletableFuture<Void> closeResult;
    /** 只在生命周期锁内读写，一旦置为 true 就不再允许启动。 */
    private boolean closed;
    /** 供启动、消费、失败回调和关闭线程共同使用，volatile 保证停止请求可见。 */
    volatile boolean running_consume;
    /** 暂停只控制后续拉取，已经入队的工作任务仍会继续执行。 */
    private volatile boolean pauseConsume;
    /** 用于识别调度回调中的关闭请求，避免调度线程等待自己退出。 */
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
            // 工作执行器准备好后才注册调度任务；调度回调不依赖工作队列的处理进度。
            if (maxConsumeSpeed > 0) {
                scheduledPool.scheduleAtFixedRate(() -> consumeCount.set(0), 1, 1, TimeUnit.SECONDS);
            }
            if (monitor_period > 0) {
                scheduledPool.scheduleAtFixedRate(() -> logger.info(monitor_log()),
                        monitor_period, monitor_period, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            closeInternal();
            throw BaseException.get(ex);
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
        // 成功启动后重复调用返回同一结果；失败结果在本轮线程清理完成后才发布，允许重试。
        if (startupResult != null && !startupResult.isCompletedExceptionally()) {
            return startupResult;
        }

        // 必须先设置运行标志，再启动线程，避免消费循环刚进入就退出。
        running_consume = true;
        try {
            // 先发布 startup 再 start，使启动/消费回调中发起的 close() 能识别当前线程归属。
            startup = new ConsumerStartup(name, consumerProp, consumerParam, this::consume,
                    () -> running_consume = false);
            startupResult = startup.start();
        } catch (RuntimeException | Error ex) {
            running_consume = false;
            logger.error("kafka consumer start error", ex);
            startupResult = CompletableFuture.failedFuture(ex);
        }
        return startupResult;
    }

    /** 请求暂停后续拉取；消费线程在下一轮检查，不会撤回本轮 poll 已返回的消息。 */
    public final void pauseConsume() {
        pauseConsume = true;
    }

    /** 撤销手动暂停；积压或速度限制仍可能使消费保持暂停。 */
    public final void resumeConsume() {
        pauseConsume = false;
    }

    /** 在所属 Kafka 消费线程运行，原生消费者由 ConsumerStartup 关闭。 */
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

    /** 只在所属 Kafka 消费线程调用；所有原生 pause/resume/poll 操作保持同线程。 */
    private void pollAndDispatch(KafkaConsumer<String, byte[]> consumer) {
        boolean throttled = isThrottled();
        // pause/resume 只改变客户端拉取状态，暂停时仍需继续 poll。
        // 每轮按实际 assignment/paused 集合操作，避免依赖重平衡前的旧分区状态。
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
        // 限制在 poll 前按批检查，批内不逐条限流，因此积压或速度可能超过阈值一批。
        // 入队后不等待工作完成；业务完成和 offset 提交需要使用方另行设计。
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

    /** 拉取或派发异常后退避；只重试下一轮循环，不自动重放本轮未派发的记录。 */
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

    /** 在 Kafka 消费线程计算路由 ID；子类重写时应快速返回，避免阻塞后续 poll。 */
    protected String id(ConsumerRecord<String, byte[]> record) {
        return record.key();
    }

    /** 向上取整为 2 的幂，使 ID 路由可通过位掩码代替取模；参数已由构造方法校验。 */
    private static int workerCount(int requested) {
        int n = -1 >>> Integer.numberOfLeadingZeros(requested - 1);
        return n < 0 ? 1 : n + 1;
    }

    /** 相同 ID 使用同一执行器，空 ID 使用第一个；重写路由时应保持同一 ID 的归属稳定。 */
    public SingleThreadEventExecutor getWorkExecutor(String id) {
        if (id == null) {
            return workPool[0];
        }
        // 将高位混入低位，减少仅使用低位做位掩码时的哈希分布偏斜。
        int hash = id.hashCode();
        return workPool[(hash ^ (hash >>> 16)) & (workThreadNum - 1)];
    }

    /** 在消费线程计算路由并入队；回调实际在选中的单线程执行器内运行。 */
    protected final void submitRecord(ConsumerRecord<String, byte[]> record) {
        SingleThreadEventExecutor executor = getWorkExecutor(id(record));
        // 必须先计数再入队，否则工作线程可能先完成并释放，造成计数短暂为负。
        blockingNum.increment();
        try {
            executor.submit(() -> work(record, executor));
        } catch (RuntimeException | Error ex) {
            blockingNum.decrement();
            // 例如执行器已关闭而拒绝任务：任务没有机会运行 finally，必须在这里回滚。
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
            // 手动模式不会在这里释放，包括回调抛异常的情况；业务应覆盖成功和失败路径。
        }
    }

    /**
     * 在按 ID 选择的单线程执行器内处理消息，可通过 executor 提交后续任务。
     * 回调异常会记录并继续处理后续消息，不自动重试当前消息。
     * 自动计数模式在本方法返回时释放积压计数；异步处理应使用手动模式，并在最终完成后释放。
     *
     * @param record 当前 Kafka 消息
     * @param executor 当前执行器，不应在回调内同步等待同一执行器的排队任务
     */
    public abstract void onMessage(ConsumerRecord<String, byte[]> record,
                                   SingleThreadEventExecutor executor) throws Exception;

    /** 由调度线程调用，输出并重置本周期监控计数；额外调用会影响下一周期的统计。 */
    public String monitor_log() {
        return StringUtil.format("name[{}] blocking[{}/{}] consume[{}/{}s] workPool[{}] work[{}/{}s]",
                name, blockingNum.sum(), maxBlockingNum,
                monitor_consumeCount.sumThenReset(), monitor_period,
                Arrays.stream(workPool).map(executor -> Integer.toString(executor.pendingTasks()))
                        .collect(Collectors.joining(",")),
                monitor_workCount.sumThenReset(), monitor_period);
    }

    /**
     * 内部线程只发起关闭，外部线程等待同一次关闭完成。
     * 统一交给独立线程执行资源回收，避免消费、工作、调度和启动协调线程等待自身退出。
     */
    @Override
    public void close() {
        boolean ownedThread = isCurrentOwnedThread();
        CompletableFuture<Void> result;
        synchronized (this) {
            if (!closed) {
                // 与 startConsume() 互斥；先封住新启动入口，再发布唯一的关闭结果。
                closed = true;
                closeResult = CompletableFuture.runAsync(this::closeResources,
                        task -> new Thread(task, name + "-shutdown").start());
            }
            result = closeResult;
        }
        // 等待必须放在生命周期锁外，且内部线程不能等待关闭线程反过来等待自己。
        if (!ownedThread) {
            result.join();
        }
    }

    /** 保留关闭异常供 closeResult 的等待者感知，同时记录异步关闭路径中的异常。 */
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
        if (startup != null) {
            // 先停止并等待 Kafka 线程，不再产生新的工作任务。
            startup.close();
        }
        // 再停止调度任务，避免关闭工作执行器时仍有监控或扫描任务提交。
        ExecutorUtil.shutdownThenAwait(true, scheduledPool);
        // 先通知全部工作执行器关闭，保留已入队任务的排空流程，再统一等待退出。
        for (SingleThreadEventExecutor executor : workPool) {
            if (executor != null) {
                executor.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            }
        }
        // 超时并不能强行终止正在阻塞的业务回调，因此回调仍应避免长期阻塞。
        for (SingleThreadEventExecutor executor : workPool) {
            if (executor != null) {
                executor.terminationFuture().syncUninterruptibly();
            }
        }
    }

    /** 包含启动协调线程，因为 start() 返回的 Future 完成回调也可能在该线程执行。 */
    private boolean isCurrentOwnedThread() {
        Thread current = Thread.currentThread();
        if (current == schedulerThread || (startup != null && startup.contains(current))) {
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
