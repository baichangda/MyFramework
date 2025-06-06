package cn.bcd.lib.base.executor.consume;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.executor.BlockingChecker;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.base.util.FloatUtil;
import cn.bcd.lib.base.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public abstract class ConsumeExecutorGroup<T> {
    public final Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String groupName;
    public final int executorNum;
    public final int executorQueueSize;
    public final boolean executorSchedule;
    public final BlockingChecker executorBlockingChecker;
    public final EntityScanner entityScanner;
    public final int monitor_period;

    public ConsumeExecutor<T>[] executors;

    /**
     * 是否运行中
     */
    volatile boolean running;

    ScheduledExecutorService scannerPool;

    /**
     * 监控信息
     */
    ScheduledExecutorService monitor_pool;
    LongAdder monitor_blockingNum;
    LongAdder monitor_entityNum;
    LongAdder monitor_messageNum;

    public ConsumeExecutorGroup(String groupName,
                                int executorNum,
                                int executorQueueSize,
                                boolean executorSchedule,
                                BlockingChecker executorBlockingChecker,
                                EntityScanner entityScanner,
                                int monitor_period) {
        this.groupName = groupName;
        this.executorNum = executorNum;
        this.executorQueueSize = executorQueueSize;
        this.executorSchedule = executorSchedule;
        this.executorBlockingChecker = executorBlockingChecker;
        this.entityScanner = entityScanner;
        this.monitor_period = monitor_period;
    }


    /**
     * 扫描并销毁过期的entity
     *
     * @param expiredInSecond 过期时间
     */
    public final void scanAndDestroyEntity(int expiredInSecond) {
        long ts = DateUtil.CacheSecond.current() - expiredInSecond;
        for (ConsumeExecutor<T> executor : executors) {
            executor.execute(() -> {
                List<String> ids = new ArrayList<>();
                for (ConsumeEntity<T> entity : executor.entityMap.values()) {
                    if (entity.lastMessageTime < ts) {
                        ids.add(entity.id);
                    }
                }
                for (String id : ids) {
                    removeEntity(id,executor);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized void init() {
        if (!running) {
            running = true;
            executors = new ConsumeExecutor[executorNum];
            for (int i = 0; i < executorNum; i++) {
                executors[i] = new ConsumeExecutor<>(groupName + "-executor(" + (i + 1) + "/" + executorNum + ")",
                        executorQueueSize,
                        executorSchedule,
                        executorBlockingChecker);
                executors[i].init();
            }

            //启动扫描过期数据
            if (entityScanner != null) {
                scannerPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, groupName + "-entityScanner"));
                scannerPool.scheduleAtFixedRate(() -> scanAndDestroyEntity(entityScanner.expiredInSecond), entityScanner.periodInSecond, entityScanner.periodInSecond, TimeUnit.SECONDS);
            }

            if (monitor_period > 0) {
                monitor_blockingNum = new LongAdder();
                monitor_entityNum = new LongAdder();
                monitor_messageNum = new LongAdder();
                monitor_pool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, groupName + "-monitor"));
                monitor_pool.scheduleAtFixedRate(() -> {
                    logger.info(monitor_log());
                }, monitor_period, monitor_period, TimeUnit.SECONDS);
            }
        }
    }

    public synchronized void destroy() {
        if (running) {
            running = false;
            List<CompletableFuture<?>> futureList = new ArrayList<>();
            for (ConsumeExecutor<T> executor : executors) {
                try {
                    futureList.add(executor.destroy(() -> {
                        for (String id : executor.entityMap.keySet()) {
                            removeEntity(id,executor);
                        }
                    }));
                } catch (Exception ex) {
                    throw BaseException.get(ex);
                }
            }
            try {
                for (CompletableFuture<?> future : futureList) {
                    future.join();
                }
            } catch (Exception ex) {
                logger.error("error", ex);
            }
            ExecutorUtil.shutdownThenAwait(scannerPool, monitor_pool);
            executors = null;
            scannerPool = null;
            monitor_pool = null;
            monitor_blockingNum = null;
            monitor_entityNum = null;
            monitor_messageNum = null;
        }

    }

    public CompletableFuture<Void> removeEntity(String id, ConsumeExecutor<T> executor) {
        return executor.submit(() -> {
            ConsumeEntity<T> remove = executor.entityMap.remove(id);
            if (remove != null) {
                try {
                    remove.destroy();
                } catch (Exception ex) {
                    logger.error("entity destroy error id[{}]", id, ex);
                }
                if (monitor_period > 0) {
                    monitor_entityNum.decrement();
                }
            }
        });
    }

    public CompletableFuture<Void> removeEntity(String id) {
        ConsumeExecutor<T> executor = getExecutor(id);
        return removeEntity(id, executor);
    }

    protected ConsumeExecutor<T> getExecutor(String id) {
        return executors[Math.floorMod(id.hashCode(), executors.length)];
    }

    public abstract String id(T t);

    public abstract ConsumeEntity<T> newEntity(String id);

    public void onMessage(T t) {
        if (monitor_period > 0) {
            monitor_blockingNum.increment();
        }
        String id = id(t);
        ConsumeExecutor<T> executor = getExecutor(id);
        executor.execute(() -> {
            ConsumeEntity<T> entity = executor.entityMap.computeIfAbsent(id, k -> {
                try {
                    ConsumeEntity<T> e = newEntity(id);
                    e.executor = executor;
                    e.init(t);
                    if (monitor_period > 0) {
                        monitor_entityNum.increment();
                    }
                    return e;
                } catch (Exception ex) {
                    logger.error("consumeEntity init error groupName[{}] id[{}]", groupName, id, ex);
                    if (monitor_period > 0) {
                        monitor_blockingNum.decrement();
                    }
                    return null;
                }
            });
            if (entity != null) {
                try {
                    entity.onMessageInternal(t);
                } catch (Exception ex) {
                    logger.error("consumeEntity onMessage error groupName[{}] id[{}]", groupName, id, ex);
                }
                if (monitor_period > 0) {
                    monitor_blockingNum.decrement();
                }
            }
            if (monitor_period > 0) {
                monitor_messageNum.increment();
            }
        });
    }

    public String monitor_log() {
        String queueLog = Arrays.stream(executors).map(e -> e.blockingQueue.size() + "").collect(Collectors.joining(" "));
        return StringUtil.format("consume group[{}] blockingNum[{}] entityNum[{}] messageSpeed[{}/s] queues[{}]",
                groupName,
                monitor_blockingNum.sum(),
                monitor_entityNum.sum(),
                FloatUtil.format(monitor_messageNum.sumThenReset() / ((double) monitor_period), 2),
                queueLog);
    }


    public static class EntityScanner {
        public final int periodInSecond;
        public final int expiredInSecond;

        private EntityScanner(int periodInSecond, int expiredInSecond) {
            this.periodInSecond = periodInSecond;
            this.expiredInSecond = expiredInSecond;
        }

        /**
         * @param periodInSecond  定时任务扫描周期(秒)
         * @param expiredInSecond 判断workHandler过期的时间(秒)
         */
        public static EntityScanner get(int periodInSecond, int expiredInSecond) {
            return new EntityScanner(periodInSecond, expiredInSecond);
        }

    }
}
