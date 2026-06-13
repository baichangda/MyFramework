package cn.bcd.lib.base.executor.consume;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.base.util.FloatUtil;
import cn.bcd.lib.base.util.StringUtil;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 按消息业务 ID 分片并串行消费的执行器组。
 * <p>
 * 相同 ID 的消息固定进入同一个 {@link ConsumeExecutor}，并由对应的
 * {@link ConsumeEntity} 顺序处理；不同分片之间可以并行执行。
 * 实体集合采用线程封闭模型，只在所属执行器线程内访问。
 * </p>
 * <p>
 * 可选功能包括实体过期扫描和吞吐、积压监控。调用 {@link #close()} 后不再接受新任务，
 * 所有存量实体会在其所属执行器线程中执行销毁回调。
 * </p>
 *
 * @param <T> 消息类型
 */
public abstract class ConsumeExecutorGroup<T> implements AutoCloseable {
    public final Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String groupName;
    public final int executorNum;
    public final int executorQueueSize;
    public final EntityScanner entityScanner;
    public final int monitorPeriod;

    public final ConsumeExecutor<T>[] executors;

    volatile boolean closed;

    final ScheduledExecutorService scannerPool;
    final ScheduledExecutorService monitorPool;
    final LongAdder monitorBlockingNum;
    final LongAdder monitorEntityNum;
    final LongAdder monitorReceiveNum;
    final LongAdder monitorWorkNum;

    /**
     * @param groupName        执行器组名称，用于线程名和日志
     * @param executorNum      期望分片数；实际数量会向上取整为 2 的幂
     * @param executorQueueSize 单个执行器的任务队列容量；{@code 0} 表示无界队列
     * @param entityScanner    实体过期扫描配置；传 {@code null} 表示不扫描
     * @param monitorPeriod    监控日志周期，单位秒；{@code 0} 表示关闭监控
     */
    @SuppressWarnings("unchecked")
    public ConsumeExecutorGroup(String groupName,
                                int executorNum,
                                int executorQueueSize,
                                EntityScanner entityScanner,
                                int monitorPeriod) {
        this.groupName = Objects.requireNonNull(groupName, "groupName");
        if (executorNum <= 0 || executorNum > (1 << 30)) {
            throw new IllegalArgumentException("executorNum must be between 1 and " + (1 << 30));
        }
        if (executorQueueSize < 0) {
            throw new IllegalArgumentException("executorQueueSize must be >= 0");
        }
        if (monitorPeriod < 0) {
            throw new IllegalArgumentException("monitorPeriod must be >= 0");
        }
        this.executorNum = tableSizeFor(executorNum);
        this.executorQueueSize = executorQueueSize;
        this.entityScanner = entityScanner;
        this.monitorPeriod = monitorPeriod;

        executors = new ConsumeExecutor[this.executorNum];
        for (int i = 0; i < this.executorNum; i++) {
            executors[i] = new ConsumeExecutor<>(
                    groupName + "-executor(" + (i + 1) + "/" + this.executorNum + ")",
                    executorQueueSize);
        }

        if (entityScanner == null) {
            scannerPool = null;
        } else {
            scannerPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, groupName + "-entityScanner"));
            scannerPool.scheduleAtFixedRate(
                    () -> scanAndDestroyEntity(entityScanner.expiredInSecond),
                    entityScanner.periodInSecond,
                    entityScanner.periodInSecond,
                    TimeUnit.SECONDS);
        }

        if (monitorPeriod > 0) {
            monitorBlockingNum = new LongAdder();
            monitorEntityNum = new LongAdder();
            monitorReceiveNum = new LongAdder();
            monitorWorkNum = new LongAdder();
            monitorPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, groupName + "-monitor"));
            monitorPool.scheduleAtFixedRate(
                    () -> logger.info(monitorLog()),
                    monitorPeriod,
                    monitorPeriod,
                    TimeUnit.SECONDS);
        } else {
            monitorBlockingNum = null;
            monitorEntityNum = null;
            monitorReceiveNum = null;
            monitorWorkNum = null;
            monitorPool = null;
        }
    }

    /**
     * 将正整数向上取整为 2 的幂，以便通过位掩码快速定位分片。
     */
    private static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : n + 1;
    }

    /**
     * 异步扫描并销毁超过指定时间未收到消息的实体。
     * 每个分片的扫描和销毁操作均提交到对应执行器线程执行。
     *
     * @param expiredInSecond 无消息存活时间，单位秒
     */
    public final void scanAndDestroyEntity(int expiredInSecond) {
        checkClosed();
        long ts = DateUtil.CacheSecond.current() - expiredInSecond;
        for (ConsumeExecutor<T> executor : executors) {
            try {
                executor.execute(() -> {
                    if (closed) {
                        return;
                    }
                    List<String> ids = new ArrayList<>();
                    for (ConsumeEntity<T> entity : executor.entityMap.values()) {
                        if (entity.lastMessageTime < ts) {
                            ids.add(entity.id);
                        }
                    }
                    for (String id : ids) {
                        removeEntityNow(id, executor);
                    }
                });
            } catch (RejectedExecutionException ex) {
                if (!closed) {
                    throw ex;
                }
            }
        }
    }

    /**
     * 停止扫描和监控，销毁全部实体，并关闭所有消费执行器。
     * <p>
     * 允许从任意线程调用，包括某个消费执行器自身的线程；重复调用不会重复关闭。
     * </p>
     */
    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        ExecutorUtil.shutdownThenAwait(true, scannerPool, monitorPool);

        List<Future<?>> cleanups = new ArrayList<>();
        for (ConsumeExecutor<T> executor : executors) {
            if (executor.inEventLoop()) {
                cleanupEntities(executor);
            } else {
                cleanups.add(executor.submit(() -> cleanupEntities(executor)));
            }
        }
        awaitAll(cleanups);

        List<Future<?>> terminations = new ArrayList<>();
        for (ConsumeExecutor<T> executor : executors) {
            Future<?> termination = executor.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            if (!executor.inEventLoop()) {
                terminations.add(termination);
            }
        }
        awaitAll(terminations);
    }

    private void cleanupEntities(ConsumeExecutor<T> executor) {
        for (ConsumeEntity<T> entity : executor.entityMap.values()) {
            destroyEntity(entity);
        }
        executor.entityMap.clear();
    }

    private void awaitAll(List<Future<?>> futures) throws InterruptedException {
        for (Future<?> future : futures) {
            future.sync();
        }
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("ConsumeExecutorGroup[" + groupName + "] is closed");
        }
    }

    /**
     * 异步删除指定实体。删除和销毁回调在实体所属执行器线程内执行。
     *
     * @param id 实体 ID
     * @return 可用于等待删除任务完成的 Future
     */
    public Future<?> removeEntity(String id) {
        checkClosed();
        ConsumeExecutor<T> executor = getExecutor(id);
        return executor.submit(() -> {
            if (!closed) {
                removeEntityNow(id, executor);
            }
        });
    }

    private void removeEntityNow(String id, ConsumeExecutor<T> executor) {
        ConsumeEntity<T> removed = executor.entityMap.remove(id);
        if (removed != null) {
            destroyEntity(removed);
            if (monitorPeriod > 0) {
                monitorEntityNum.decrement();
            }
        }
    }

    /**
     * 当条件返回 {@code true} 时异步删除并销毁实体。
     * 条件判断在实体所属执行器线程内执行，因此可以安全读取实体状态。
     *
     * @param id        实体 ID
     * @param predicate 删除条件，返回 {@code true} 表示删除
     * @return 可用于等待判断及删除任务完成的 Future
     */
    public Future<?> removeEntityIf(String id, Predicate<ConsumeEntity<T>> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        checkClosed();
        ConsumeExecutor<T> executor = getExecutor(id);
        return executor.submit(() -> {
            if (closed) {
                return;
            }
            ConsumeEntity<T> entity = executor.entityMap.get(id);
            if (entity != null && predicate.test(entity)) {
                removeEntityNow(id, executor);
            }
        });
    }

    /**
     * @deprecated 使用 {@link #removeEntityIf(String, Predicate)}，返回 true 表示删除。
     */
    @Deprecated
    public Future<?> checkRemoveEntity(String id, Function<ConsumeEntity<T>, Boolean> func) {
        Objects.requireNonNull(func, "func");
        return removeEntityIf(id, entity -> Boolean.TRUE.equals(func.apply(entity)));
    }

    protected ConsumeExecutor<T> getExecutor(String id) {
        Objects.requireNonNull(id, "id");
        int h = id.hashCode();
        h ^= h >>> 16;
        return executors[h & (executorNum - 1)];
    }

    /**
     * 从消息中提取用于分片和实体查找的业务 ID。
     *
     * @param t 消息
     * @return 非空业务 ID
     */
    public abstract String id(T t);

    /**
     * 异步查询实体。查询操作在实体所属执行器线程内执行。
     *
     * @param id 实体 ID
     * @return 返回实体或 {@code null} 的 Future
     */
    public Future<ConsumeEntity<T>> getEntity(String id) {
        checkClosed();
        ConsumeExecutor<T> executor = getExecutor(id);
        return executor.submit(() -> {
            checkClosed();
            return executor.entityMap.get(id);
        });
    }

    /**
     * 创建新的实体实例。框架随后会设置执行器并调用
     * {@link ConsumeEntity#init(Object)}。
     *
     * @param id    实体 ID
     * @param first 触发创建的第一条消息
     * @return 新实体，不能为 {@code null}
     */
    public abstract ConsumeEntity<T> newEntity(String id, T first);

    /**
     * 接收一条消息并异步提交到对应分片。
     * <p>
     * 如果实体不存在，会先创建和初始化实体，再将当前消息作为第一条消息处理。
     * 有界队列已满或执行器已关闭时会抛出
     * {@link RejectedExecutionException}。
     * </p>
     *
     * @param t 消息
     */
    public void onMessage(T t) {
        checkClosed();
        String id = Objects.requireNonNull(id(t), "id");
        ConsumeExecutor<T> executor = getExecutor(id);

        if (monitorPeriod > 0) {
            monitorBlockingNum.increment();
            monitorReceiveNum.increment();
        }
        try {
            executor.execute(() -> consumeMessage(executor, id, t));
        } catch (RuntimeException ex) {
            if (monitorPeriod > 0) {
                monitorBlockingNum.decrement();
            }
            throw ex;
        }
    }

    private void consumeMessage(ConsumeExecutor<T> executor, String id, T message) {
        try {
            if (closed) {
                return;
            }
            ConsumeEntity<T> entity = executor.entityMap.get(id);
            if (entity == null) {
                entity = createEntity(executor, id, message);
                if (entity == null) {
                    return;
                }
                executor.entityMap.put(id, entity);
                if (monitorPeriod > 0) {
                    monitorEntityNum.increment();
                }
            }
            try {
                entity.onMessageInternal(message);
            } catch (Exception ex) {
                logger.error("consumeEntity onMessage error groupName[{}] id[{}]", groupName, id, ex);
            }
            if (monitorPeriod > 0) {
                monitorWorkNum.increment();
            }
        } finally {
            if (monitorPeriod > 0) {
                monitorBlockingNum.decrement();
            }
        }
    }

    private ConsumeEntity<T> createEntity(ConsumeExecutor<T> executor, String id, T first) {
        ConsumeEntity<T> entity = null;
        try {
            entity = Objects.requireNonNull(newEntity(id, first), "newEntity returned null");
            entity.executor = executor;
            entity.init(first);
            return entity;
        } catch (Exception ex) {
            logger.error("consumeEntity init error groupName[{}] id[{}]", groupName, id, ex);
            if (entity != null) {
                destroyEntity(entity);
            }
            return null;
        }
    }

    private void destroyEntity(ConsumeEntity<T> entity) {
        try {
            entity.destroy();
        } catch (Exception ex) {
            logger.error("entity destroy error id[{}]", entity.id, ex);
        }
    }

    /**
     * 生成当前执行器组的队列、实体、吞吐和积压监控文本。
     */
    public String monitorLog() {
        String queueLog = Arrays.stream(executors)
                .map(e -> String.valueOf(e.pendingTasks()))
                .collect(Collectors.joining(" "));
        if (monitorPeriod <= 0) {
            return StringUtil.format("consume group[{}] monitor disabled queues[{}]", groupName, queueLog);
        }
        return StringUtil.format(
                "consume group[{}] blockingNum[{}] entityNum[{}] receiveSpeed[{}/s] queues[{}] workSpeed[{}/s]",
                groupName,
                monitorBlockingNum.sum(),
                monitorEntityNum.sum(),
                FloatUtil.format(monitorReceiveNum.sumThenReset() / (double) monitorPeriod, 2),
                queueLog,
                FloatUtil.format(monitorWorkNum.sumThenReset() / (double) monitorPeriod, 2));
    }

    public static class EntityScanner {
        public final int periodInSecond;
        public final int expiredInSecond;

        private EntityScanner(int periodInSecond, int expiredInSecond) {
            if (periodInSecond <= 0) {
                throw new IllegalArgumentException("periodInSecond must be > 0");
            }
            if (expiredInSecond < 0) {
                throw new IllegalArgumentException("expiredInSecond must be >= 0");
            }
            this.periodInSecond = periodInSecond;
            this.expiredInSecond = expiredInSecond;
        }

        /**
         * 创建实体过期扫描配置。
         *
         * @param periodInSecond  扫描周期，单位秒，必须大于 0
         * @param expiredInSecond 实体无消息过期时间，单位秒，不能小于 0
         */
        public static EntityScanner get(int periodInSecond, int expiredInSecond) {
            return new EntityScanner(periodInSecond, expiredInSecond);
        }
    }
}
