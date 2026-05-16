package cn.bcd.lib.base.executor.consume;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.base.util.FloatUtil;
import cn.bcd.lib.base.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class ConsumeExecutorGroup<T> implements AutoCloseable {
    public final Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String groupName;
    public final int executorNum;
    public final int executorQueueSize;
    public final boolean executorSchedule;
    public final EntityScanner entityScanner;
    public final int monitor_period;

    public final ConsumeExecutor<T>[] executors;

    /**
     * 是否关闭
     */
    boolean closed;

    final ScheduledExecutorService scannerPool;

    /**
     * 监控信息
     */
    final ScheduledExecutorService monitorPool;
    final LongAdder monitorBlockingNum;
    final LongAdder monitorEntityNum;
    final LongAdder monitorReceiveNum;
    final LongAdder monitorWorkNum;

    public ConsumeExecutorGroup(String groupName,
                                int executorNum,
                                int executorQueueSize,
                                boolean executorSchedule,
                                EntityScanner entityScanner,
                                int monitor_period) {
        this.groupName = groupName;
        this.executorNum = tableSizeFor(executorNum);
        this.executorQueueSize = executorQueueSize;
        this.executorSchedule = executorSchedule;
        this.entityScanner = entityScanner;
        this.monitor_period = monitor_period;

        //创建线程池
        executors = new ConsumeExecutor[this.executorNum];
        for (int i = 0; i < this.executorNum; i++) {
            executors[i] = new ConsumeExecutor<>(groupName + "-executor(" + (i + 1) + "/" + this.executorNum + ")",
                    executorQueueSize,
                    executorSchedule);
        }
        //启动扫描过期数据
        if (entityScanner == null) {
            scannerPool = null;
        } else {
            scannerPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, groupName + "-entityScanner"));
            scannerPool.scheduleAtFixedRate(() -> scanAndDestroyEntity(entityScanner.expiredInSecond), entityScanner.periodInSecond, entityScanner.periodInSecond, TimeUnit.SECONDS);
        }
        if (monitor_period > 0) {
            monitorBlockingNum = new LongAdder();
            monitorEntityNum = new LongAdder();
            monitorReceiveNum = new LongAdder();
            monitorWorkNum = new LongAdder();
            monitorPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, groupName + "-monitor"));
            monitorPool.scheduleAtFixedRate(() -> {
                logger.info(monitorLog());
            }, monitor_period, monitor_period, TimeUnit.SECONDS);
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
                    removeEntity(id, executor);
                }
            });
        }
    }


    @Override
    public void close() throws Exception {
        if (!closed) {
            closed = true;
            //销毁entity、停止线程池
            for (ConsumeExecutor<T> executor : executors) {
                for (String id : executor.entityMap.keySet()) {
                    removeEntity(id, executor);
                }
                executor.shutdown();
            }
            //等待线程池停止
            for (ConsumeExecutor<T> executor : executors) {
                ExecutorUtil.await(executor);
            }
            ExecutorUtil.shutdownThenAwait(true, scannerPool, monitorPool);
        }
    }

    public Future<?> removeEntity(String id) {
        ConsumeExecutor<T> executor = getExecutor(id);
        return removeEntity(id,executor);
    }

    private Future<?> removeEntity(String id, ConsumeExecutor<T> executor) {
        return executor.submit(() -> {
            ConsumeEntity<T> remove = executor.entityMap.remove(id);
            if (remove != null) {
                try {
                    remove.destroy();
                } catch (Exception ex) {
                    logger.error("entity destroy error id[{}]", id, ex);
                }
                if (monitor_period > 0) {
                    monitorEntityNum.decrement();
                }
            }
        });
    }

    public Future<?> checkRemoveEntity(String id, Function<ConsumeEntity<T>, Boolean> func) {
        ConsumeExecutor<T> executor = getExecutor(id);
        return executor.submit(() -> {
            ConsumeEntity<T> entity = executor.entityMap.get(id);
            Boolean save = func.apply(entity);
            if (!save) {
                executor.entityMap.remove(id);
                try {
                    entity.destroy();
                } catch (Exception ex) {
                    logger.error("entity destroy error id[{}]", id, ex);
                }
                if (monitor_period > 0) {
                    monitorEntityNum.decrement();
                }
            }
        });
    }

    protected ConsumeExecutor<T> getExecutor(String id) {
        int h = id.hashCode();
        h = h ^ (h >>> 16);
        return executors[h & (executorNum - 1)];
    }

    public abstract String id(T t);

    public Future<ConsumeEntity<T>> getEntity(String id) {
        ConsumeExecutor<T> executor = getExecutor(id);
        return executor.submit(() -> executor.entityMap.get(id));
    }

    public abstract ConsumeEntity<T> newEntity(String id, T first);

    public void onMessage(T t) {
        if (monitor_period > 0) {
            monitorBlockingNum.increment();
        }
        String id = id(t);
        ConsumeExecutor<T> executor = getExecutor(id);
        if (monitor_period > 0) {
            monitorReceiveNum.increment();
        }
        executor.execute(() -> {
            ConsumeEntity<T> entity = executor.entityMap.computeIfAbsent(id, k -> {
                try {
                    ConsumeEntity<T> e = newEntity(id, t);
                    e.executor = executor;
                    e.init(t);
                    if (monitor_period > 0) {
                        monitorEntityNum.increment();
                    }
                    return e;
                } catch (Exception ex) {
                    logger.error("consumeEntity init error groupName[{}] id[{}]", groupName, id, ex);
                    if (monitor_period > 0) {
                        monitorBlockingNum.decrement();
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
                    monitorBlockingNum.decrement();
                }
            }
            if (monitor_period > 0) {
                monitorWorkNum.increment();
            }
        });
    }

    public String monitorLog() {
        String queueLog = Arrays.stream(executors).map(e -> e.blockingQueue.size() + "").collect(Collectors.joining(" "));
        return StringUtil.format("consume group[{}] blockingNum[{}] entityNum[{}] receiveSpeed[{}/s] queues[{}] workSpeed[{}/s]",
                groupName,
                monitorBlockingNum.sum(),
                monitorEntityNum.sum(),
                FloatUtil.format(monitorReceiveNum.sumThenReset() / ((double) monitor_period), 2),
                queueLog,
                FloatUtil.format(monitorWorkNum.sumThenReset() / ((double) monitor_period), 2)
        );
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
