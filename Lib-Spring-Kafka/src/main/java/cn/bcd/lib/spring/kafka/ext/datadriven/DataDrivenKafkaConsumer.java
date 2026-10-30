package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.FloatUtil;
import cn.bcd.lib.base.util.StringUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.spring.kafka.ext.KafkaConsumerGroup;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * 按业务 ID 将消息路由到固定的工作执行器，并为每个 ID 维护一个 WorkHandler。
 * Handler 的创建、初始化、消息处理和销毁均在所属执行器内串行执行。
 * 限速重置、监控和过期扫描共用独立调度线程；关闭时先停止消费，再清理 Handler。
 */
public abstract class DataDrivenKafkaConsumer implements AutoCloseable {
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

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration PAUSED_POLL_TIMEOUT = Duration.ofMillis(100);
    private CompletableFuture<Void> startupResult;
    private CompletableFuture<Void> closeResult;
    private boolean closed;
    volatile boolean running_consume;
    private volatile boolean pauseConsume;
    private volatile Thread schedulerThread;

    public final int workExecutorNum;
    public final WorkExecutor[] workExecutors;
    public final WorkHandlerScanner workHandlerScanner;
    public final LongAdder monitor_workHandlerCount = new LongAdder();

    public static class WorkHandlerScanner {
        public final int periodInSecond;
        public final int expiredInSecond;

        private WorkHandlerScanner(int periodInSecond, int expiredInSecond) {
            this.periodInSecond = periodInSecond;
            this.expiredInSecond = expiredInSecond;
        }

        /**
         * @param periodInSecond  定时任务扫描周期(秒)
         * @param expiredInSecond 判断workHandler过期的时间(秒)
         */
        public static WorkHandlerScanner get(int periodInSecond, int expiredInSecond) {
            return new WorkHandlerScanner(periodInSecond, expiredInSecond);
        }

    }

    /**
     * @param name                当前消费者的名称(用于标定线程名称)
     * @param workExecutorNum     工作任务执行器个数、会向上取整到2的幂
     * @param maxBlockingNum      最大阻塞数量(0代表不限制)、当内存中达到最大阻塞数量时候、消费者会停止消费
     *                            当不限制时候、还是会记录{@link #blockingNum}、便于监控阻塞数量
     * @param autoReleaseBlocking 是否自动释放阻塞、适用于工作内容为同步处理的逻辑
     * @param maxConsumeSpeed     最大消费速度每秒(0代表不限制)、kafka一次消费一批数据、设置过小会导致不起作用、此时会每秒处理一批数据
     *                            每消费一次的数据量大小取决于如下消费者参数
     *                            {@link ConsumerConfig#MAX_POLL_RECORDS_CONFIG} 一次poll消费最大数据量
     *                            {@link ConsumerConfig#MAX_PARTITION_FETCH_BYTES_CONFIG} 每个分区最大拉取字节数
     * @param workHandlerScanner  定时扫描并销毁过期的{@link WorkHandler}、销毁时候会执行其{@link WorkHandler#destroy()}方法、由对应的工作任务执行器执行
     *                            null则代表不启动扫描
     * @param monitor_period      监控信息打印周期(秒)、0则代表不打印
     * @param consumerParam       消费者的参数、不能为null
     *                            主要用于设置消费的topic、分区、消费线程、消费者开始消费的位置
     *                            具体参考{@link ConsumerParam}中静态方法
     */
    public DataDrivenKafkaConsumer(String name,
                                   int workExecutorNum,
                                   int maxBlockingNum,
                                   boolean autoReleaseBlocking,
                                   int maxConsumeSpeed,
                                   WorkHandlerScanner workHandlerScanner,
                                   int monitor_period,
                                   ConsumerParam consumerParam) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (workExecutorNum <= 0 || workExecutorNum > (1 << 30)) {
            throw new IllegalArgumentException("worker count must be between 1 and 2^30");
        }
        if (maxBlockingNum < 0 || maxConsumeSpeed < 0 || monitor_period < 0) {
            throw new IllegalArgumentException("blocking limit, consume speed and monitor period cannot be negative");
        }
        validateScanner(workHandlerScanner);
        this.name = name;
        this.maxBlockingNum = maxBlockingNum;
        this.autoReleaseBlocking = autoReleaseBlocking;
        this.maxConsumeSpeed = maxConsumeSpeed;
        this.monitor_period = monitor_period;
        this.consumerParam = Objects.requireNonNull(consumerParam, "consumerParam");
        scheduledPool = maxConsumeSpeed == 0 && monitor_period == 0 && workHandlerScanner == null ? null
                : Executors.newSingleThreadScheduledExecutor(task -> {
                    schedulerThread = new Thread(task, name + "-schedule");
                    return schedulerThread;
                });
        this.workExecutorNum = workerCount(workExecutorNum);
        this.workHandlerScanner = workHandlerScanner;
        workExecutors = new WorkExecutor[this.workExecutorNum];
        try {
            for (int i = 0; i < this.workExecutorNum; i++) {
                String workerName = name + "-worker(" + (i + 1) + "/" + this.workExecutorNum + ")";
                workExecutors[i] = new WorkExecutor(r -> new Thread(r, workerName));
            }
            if (maxConsumeSpeed > 0) {
                scheduledPool.scheduleAtFixedRate(() -> consumeCount.set(0), 1, 1, TimeUnit.SECONDS);
            }
            if (monitor_period > 0) {
                scheduledPool.scheduleAtFixedRate(() -> logger.info(monitor_log()),
                        monitor_period, monitor_period, TimeUnit.SECONDS);
            }
            if (workHandlerScanner != null) {
                scheduledPool.scheduleAtFixedRate(
                        () -> scanAndDestroyWorkHandler(workHandlerScanner.expiredInSecond),
                        workHandlerScanner.periodInSecond, workHandlerScanner.periodInSecond, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            closeInternal();
            throw BaseException.get(ex);
        }
    }

    private static void validateScanner(WorkHandlerScanner scanner) {
        if (scanner != null && (scanner.periodInSecond <= 0 || scanner.expiredInSecond <= 0)) {
            throw new IllegalArgumentException("workHandlerScanner periods must be positive");
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

    /**
     * 根据id分配到对应的工作执行者上
     *
     * @param id
     * @return
     */
    public WorkExecutor getWorkExecutor(String id) {
        if (id == null) {
            return workExecutors[0];
        }
        int hash = id.hashCode();
        return workExecutors[(hash ^ (hash >>> 16)) & (workExecutorNum - 1)];
    }

    /**
     * 根据id构造workHandler
     *
     * @param id
     * @return
     */
    public abstract WorkHandler newHandler(String id, ConsumerRecord<String, byte[]> first);

    /**
     * 移除workHandler
     *
     * @param id
     * @return
     */
    public final Future<?> removeHandler(String id) {
        WorkExecutor executor = getWorkExecutor(id);
        if (executor.inEventLoop()) {
            destroyHandler(id, executor);
            return CompletableFuture.completedFuture(null);
        }
        return executor.submit(() -> destroyHandler(id, executor));
    }

    private void destroyHandler(String id, WorkExecutor executor) {
        WorkHandler workHandler = executor.workHandlers.remove(id);
        if (workHandler != null) {
            try {
                workHandler.destroy();
            } catch (Exception ex) {
                logger.error("workHandler destroy error id[{}]", workHandler.id, ex);
            }
            if (monitor_period > 0) {
                monitor_workHandlerCount.decrement();
            }
        }
    }

    /**
     * 根据id获取对应WorkHandler
     *
     * @param id
     * @return
     */
    @SuppressWarnings("unchecked")
    public final <V extends WorkHandler> V getHandler(String id) {
        WorkExecutor workExecutor = getWorkExecutor(id);
        if (workExecutor.inEventLoop()) {
            return (V) workExecutor.workHandlers.get(id);
        }
        try {
            return (V) workExecutor.submit(() -> workExecutor.workHandlers.get(id)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BaseException.get(e);
        } catch (ExecutionException e) {
            throw BaseException.get(e);
        }
    }


    protected final void submitRecord(ConsumerRecord<String, byte[]> record) {
        String id = id(record);
        WorkExecutor executor = getWorkExecutor(id);
        blockingNum.increment();
        try {
            executor.submit(() -> work(id, record, executor));
        } catch (RuntimeException | Error ex) {
            blockingNum.decrement();
            throw ex;
        }
        if (monitor_period > 0) {
            monitor_consumeCount.increment();
        }
    }

    private void work(String id, ConsumerRecord<String, byte[]> record, WorkExecutor executor) {
        WorkHandler handler = null;
        try {
            handler = executor.workHandlers.computeIfAbsent(id, ignored -> createHandler(id, record, executor));
            if (handler != null) {
                handler.lastMessageTime = DateUtil.CacheSecond.current();
                handler.onMessage(record);
            }
        } catch (Exception ex) {
            logger.error("workHandler onMessage error id[{}]", id, ex);
        } finally {
            // 初始化失败的消息也必须释放计数，即使正常消息由业务异步释放。
            if (autoReleaseBlocking || handler == null) {
                blockingNum.decrement();
            }
            if (monitor_period > 0 && handler != null) {
                monitor_workCount.increment();
            }
        }
    }

    private WorkHandler createHandler(String id, ConsumerRecord<String, byte[]> first, WorkExecutor executor) {
        try {
            WorkHandler handler = newHandler(id, first);
            handler.afterConstruct(executor, this);
            handler.init(first);
            if (monitor_period > 0) {
                monitor_workHandlerCount.increment();
            }
            return handler;
        } catch (Exception ex) {
            logger.error("workHandler init error id[{}]", id, ex);
            return null;
        }
    }

    /**
     * 监控日志
     * 如果需要修改日志、可以重写此方法
     * <p>
     * workExecutor 任务执行器个数
     * workHandler 任务处理器个数
     * blocking 当前阻塞数量/最大阻塞数量
     * consumeSpeed 每秒消费速度
     * queues 各执行器等待执行的任务数量
     * workSpeed 每秒工作速度
     */
    public String monitor_log() {
        double consumeSpeed = FloatUtil.round(monitor_consumeCount.sumThenReset() / (double) monitor_period, 2);
        double workSpeed = FloatUtil.round(monitor_workCount.sumThenReset() / (double) monitor_period, 2);
        String queues = Arrays.stream(workExecutors)
                .map(executor -> Integer.toString(executor.pendingTasks())).collect(Collectors.joining(" "));
        return StringUtil.format("name[{}] workExecutor[{}] workHandler[{}] blocking[{}/{}] "
                        + "consumeSpeed[{}/s] queues[{}] workSpeed[{}/s]",
                name, workExecutorNum, monitor_workHandlerCount.sum(), blockingNum.sum(), maxBlockingNum,
                consumeSpeed, queues, workSpeed);
    }

    /**
     * 扫描并销毁过期的workHandler
     *
     * @param expiredInSecond 过期时间
     */
    public final void scanAndDestroyWorkHandler(int expiredInSecond) {
        long ts = DateUtil.CacheSecond.current() - expiredInSecond;
        for (WorkExecutor executor : workExecutors) {
            executor.submit(() -> {
                for (String id : new ArrayList<>(executor.workHandlers.keySet())) {
                    WorkHandler handler = executor.workHandlers.get(id);
                    if (handler != null && handler.lastMessageTime < ts) {
                        destroyHandler(id, executor);
                    }
                }
            });
        }
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
        for (WorkExecutor executor : workExecutors) {
            if (executor != null) {
                executor.addShutdownHook(() -> {
                    for (String id : new ArrayList<>(executor.workHandlers.keySet())) {
                        destroyHandler(id, executor);
                    }
                });
                executor.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            }
        }
        for (WorkExecutor executor : workExecutors) {
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
        for (WorkExecutor executor : workExecutors) {
            if (executor != null && executor.inEventLoop()) {
                return true;
            }
        }
        return false;
    }
}
