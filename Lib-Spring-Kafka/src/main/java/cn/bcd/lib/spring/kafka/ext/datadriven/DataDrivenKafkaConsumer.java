package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.FloatUtil;
import cn.bcd.lib.base.util.StringUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.ConsumerGroup;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.InterruptException;
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
 * 相同 ID 的消息按入队顺序执行；多个 Kafka 分区或消费线程之间不提供全局消息顺序。
 * 若 Handler 发起异步操作，异步完成顺序及资源生命周期由业务协调。
 */
public abstract class DataDrivenKafkaConsumer implements AutoCloseable {
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
     * LongAdder 的 sum 用于近似积压判断，不是严格的容量预留，也不表示 Kafka offset 已提交。
     */
    public final LongAdder blockingNum = new LongAdder();
    /** 多个消费线程共用的秒级计数，由调度线程每秒归零，属于按批检查的固定窗口限速。 */
    public final AtomicInteger consumeCount = new AtomicInteger();
    /** 仅在开启监控时累计；monitor_log() 会读取并重置本周期的计数。 */
    public final LongAdder monitor_consumeCount = new LongAdder();
    /** 已进入 Handler 消息回调的次数，包含回调抛异常的情况，不代表业务成功数。 */
    public final LongAdder monitor_workCount = new LongAdder();
    /** 独立于工作执行器，避免业务阻塞时连带阻塞限速重置或监控；无调度任务时为 null。 */
    public final ScheduledExecutorService scheduledPool;
    /** 本组独立消费线程的持有者，供关闭线程和内部线程识别使用。 */
    private volatile ConsumerGroup consumerGroup;

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration PAUSED_POLL_TIMEOUT = Duration.ofMillis(100);
    /** 由 synchronized(this) 保护，所有外部关闭调用等待同一个完成结果。 */
    private CompletableFuture<Void> closeResult;
    /** 只在生命周期锁内读写，一旦置为 true 就不再允许启动。 */
    private boolean closed;
    /** 组级消费开关，启动时打开、关闭时停止；单个线程退出不会修改，也不代表线程均已初始化。 */
    volatile boolean running_consume;
    /** 暂停只控制后续拉取，已经入队的工作任务仍会继续执行。 */
    private volatile boolean pauseConsume;
    /** 用于识别调度回调中的关闭请求，避免调度线程等待自己退出。 */
    private volatile Thread schedulerThread;

    public final int workExecutorNum;
    /** 每个执行器独占自己的 Handler Map；框架通过任务队列访问，不在消费线程直接读写。 */
    public final WorkExecutor[] workExecutors;
    public final WorkHandlerScanner workHandlerScanner;
    public final LongAdder monitor_workHandlerCount = new LongAdder();

    /** 按消息空闲时间清理 Handler；过期依据本地处理时间，不使用 Kafka 消息时间戳。 */
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
            // 工作执行器准备好后才注册调度任务；调度回调不依赖工作队列的处理进度。
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
     * 发起各消费线程的启动后返回，不等待初始化，也不提供启动 Future。
     * 各线程初始化失败只记录日志并退出，其他线程继续运行；重复调用不会重建失败线程。
     * 关闭后禁止启动；准备线程前的配置异常在当前线程记录，准备失败时允许再次调用。
     */
    public synchronized void startConsume(Map<String, Object> consumerProp) {
        if (closed) {
            throw new IllegalStateException("consumer already closed");
        }
        if (consumerGroup != null) {
            return;
        }

        running_consume = true;
        try {
            // 先发布 consumerGroup 再 start，使消费回调内的 close() 能识别所属线程。
            consumerGroup = new ConsumerGroup(name, consumerProp, consumerParam, this::consume);
            consumerGroup.start();
        } catch (RuntimeException | Error ex) {
            running_consume = false;
            logger.error("kafka consumer start error", ex);
        }
    }

    /** 请求暂停后续拉取；消费线程在下一轮检查，不会撤回本轮 poll 已返回的消息。 */
    public final void pauseConsume() {
        pauseConsume = true;
    }

    /** 撤销手动暂停；积压或速度限制仍可能使消费保持暂停。 */
    public final void resumeConsume() {
        pauseConsume = false;
    }

    /** 在所属 Kafka 消费线程运行，原生消费者由 ConsumerGroup 关闭。 */
    public void consume(KafkaConsumer<String, byte[]> consumer) {
        // 中断只结束当前线程；组级 running_consume 只由统一启动/关闭流程维护。
        while (running_consume && !Thread.currentThread().isInterrupted()) {
            try {
                pollAndDispatch(consumer);
            } catch (Exception ex) {
                // Kafka 的中断异常与普通 poll 错误区别处理，防止关闭后继续退避和轮询。
                if (ex instanceof InterruptException) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (!running_consume || Thread.currentThread().isInterrupted() || !retryAfterFailure(ex)) {
                    break;
                }
            }
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
        // 限制在 poll 前按批检查，批内不逐条限流；多消费线程可同时拉取，积压或速度可超出阈值。
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

    /**
     * 相同 ID 固定映射到同一执行器，null ID 使用第一个执行器。
     * 子类重写时必须保持同一 ID 的执行器归属稳定，否则会破坏 Handler 的串行访问约定。
     */
    public WorkExecutor getWorkExecutor(String id) {
        if (id == null) {
            return workExecutors[0];
        }
        // 将高位混入低位，减少仅使用低位做位掩码时的哈希分布偏斜。
        int hash = id.hashCode();
        return workExecutors[(hash ^ (hash >>> 16)) & (workExecutorNum - 1)];
    }

    /**
     * 在该 ID 所属的工作执行器中创建 Handler；框架随后绑定 executor/consumer 并调用 init。
     * 应返回尚未初始化的新实例。初始化成功后，first 仍会正常交给 onMessage 处理。
     *
     * @param id 消息的业务 ID，可能为 null
     * @param first 触发本次 Handler 创建的第一条消息
     * @return 对应该 ID 的 Handler
     */
    public abstract WorkHandler newHandler(String id, ConsumerRecord<String, byte[]> first);

    /**
     * 在所属执行器中移除并销毁 Handler；后续同 ID 消息到达时会重新创建。
     * 已在所属执行器内时直接执行，否则排队，返回的 Future 表示本次移除任务结束。
     * destroy 抛出的 Exception 会记录并吞掉，不会使返回的 Future 异常完成。
     *
     * @param id 要移除的业务 ID
     * @return 移除任务的完成结果
     */
    public final Future<?> removeHandler(String id) {
        WorkExecutor executor = getWorkExecutor(id);
        if (executor.inEventLoop()) {
            // 允许业务在当前回调中 removeHandler(id).get()，避免向自己排队后再等待。
            destroyHandler(id, executor);
            return CompletableFuture.completedFuture(null);
        }
        return executor.submit(() -> destroyHandler(id, executor));
    }

    /** 只在所属执行器中调用；先从 Map 移除，再释放资源，重复移除不重复销毁。 */
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
     * 在所属执行器中读取 Handler，外部调用会等待该读取任务执行。
     * 返回对象并不意味着调用方可跨线程修改它；后续状态操作仍应提交给 handler.executor。
     * 避免两个工作执行器互相同步调用此方法，形成相互等待。
     *
     * @param id 要查找的业务 ID
     * @return 当前 Handler，不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    public final <V extends WorkHandler> V getHandler(String id) {
        WorkExecutor workExecutor = getWorkExecutor(id);
        if (workExecutor.inEventLoop()) {
            // 同线程读取不排队，避免在自己的队列上同步等待。
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


    /** 消费线程只计算路由并提交任务；Handler 的查询和创建推迟到工作线程中。 */
    protected final void submitRecord(ConsumerRecord<String, byte[]> record) {
        String id = id(record);
        WorkExecutor executor = getWorkExecutor(id);
        // 必须先计数再入队，否则工作线程可能先完成并释放，造成计数短暂为负。
        blockingNum.increment();
        try {
            executor.submit(() -> work(id, record, executor));
        } catch (RuntimeException | Error ex) {
            blockingNum.decrement();
            // 例如执行器已关闭而拒绝任务：任务没有机会运行 finally，必须在这里回滚。
            throw ex;
        }
        if (monitor_period > 0) {
            monitor_consumeCount.increment();
        }
    }

    private void work(String id, ConsumerRecord<String, byte[]> record, WorkExecutor executor) {
        WorkHandler handler = null;
        try {
            // 同一 ID 的查询、创建和注册在所属单线程执行器内完成，无需并发 Map 或额外锁。
            handler = executor.workHandlers.computeIfAbsent(id, ignored -> createHandler(id, record, executor));
            if (handler != null) {
                // 记录开始处理的本地秒时间，供空闲扫描判断；并非业务异步处理完成时间。
                handler.lastMessageTime = DateUtil.CacheSecond.current();
                handler.onMessage(record);
            }
        } catch (Exception ex) {
            logger.error("workHandler onMessage error id[{}]", id, ex);
        } finally {
            // 初始化失败的消息也必须释放计数，即使正常消息由业务异步释放。
            // 手动模式下，只要 Handler 已建立，消息回调失败后的释放也由业务负责，避免重复释放。
            if (autoReleaseBlocking || handler == null) {
                blockingNum.decrement();
            }
            if (monitor_period > 0 && handler != null) {
                monitor_workCount.increment();
            }
        }
    }

    /** 初始化成功才注册；失败返回 null，使下一条同 ID 消息能重新尝试创建。 */
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
     * 本方法读取并重置监控周期计数，不适合作为无副作用的实时查询接口；由调度线程定期调用。
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
     * 向每个工作执行器提交空闲 Handler 扫描任务，本方法不等待扫描完成。
     * 调度线程只提交任务，Map 遍历和销毁仍由所属执行器执行，避免与消息处理并发访问。
     * 空闲时间不代表所有外部异步操作已完成，业务应自行协调异步资源的销毁。
     *
     * @param expiredInSecond 允许的消息空闲时间，单位秒
     */
    public final void scanAndDestroyWorkHandler(int expiredInSecond) {
        long ts = DateUtil.CacheSecond.current() - expiredInSecond;
        for (WorkExecutor executor : workExecutors) {
            executor.submit(() -> {
                // 复制 key 后再删除 Map 元素；最后处理时间在任务实际执行时读取。
                for (String id : new ArrayList<>(executor.workHandlers.keySet())) {
                    WorkHandler handler = executor.workHandlers.get(id);
                    if (handler != null && handler.lastMessageTime < ts) {
                        destroyHandler(id, executor);
                    }
                }
            });
        }
    }

    /**
     * 内部线程只发起关闭，外部线程等待同一次关闭完成。
     * 统一交给独立线程执行资源回收，避免消费、工作和调度线程等待自身退出。
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
        if (consumerGroup != null) {
            // 先停止并等待 Kafka 线程，不再产生新的工作任务。
            consumerGroup.close();
        }
        // 再停止调度任务，避免关闭工作执行器时仍有监控或扫描任务提交。
        ExecutorUtil.shutdownThenAwait(true, scheduledPool);
        for (WorkExecutor executor : workExecutors) {
            if (executor != null) {
                // 让 Netty 在运行待执行任务后执行销毁钩子，避免 Handler 先销毁而消息仍在排队。
                executor.addShutdownHook(() -> {
                    for (String id : new ArrayList<>(executor.workHandlers.keySet())) {
                        destroyHandler(id, executor);
                    }
                });
                executor.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            }
        }
        // 先通知全部执行器关闭，再逐个等待，避免第一个执行器的等待延迟其他执行器收到关闭请求。
        // Netty 的关闭超时不能强行终止正在阻塞的业务回调，业务任务仍应避免长期阻塞。
        for (WorkExecutor executor : workExecutors) {
            if (executor != null) {
                executor.terminationFuture().syncUninterruptibly();
            }
        }
    }

    /** 消费、工作和调度线程均只能发起关闭，不能同步等待包含自己的资源回收。 */
    private boolean isCurrentOwnedThread() {
        Thread current = Thread.currentThread();
        if (current == schedulerThread || (consumerGroup != null && consumerGroup.contains(current))) {
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
