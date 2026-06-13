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

    private static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : n + 1;
    }

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
     * 当 predicate 返回 true 时销毁 entity。
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

    public abstract String id(T t);

    public Future<ConsumeEntity<T>> getEntity(String id) {
        checkClosed();
        ConsumeExecutor<T> executor = getExecutor(id);
        return executor.submit(() -> {
            checkClosed();
            return executor.entityMap.get(id);
        });
    }

    public abstract ConsumeEntity<T> newEntity(String id, T first);

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

        public static EntityScanner get(int periodInSecond, int expiredInSecond) {
            return new EntityScanner(periodInSecond, expiredInSecond);
        }
    }
}
