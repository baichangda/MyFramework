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
import java.util.concurrent.ExecutionException;
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
    public final int monitorPeriod;

    public final ConsumeExecutor<T>[] executors;

    /**
     * 是否关闭
     */
    volatile boolean closed;

    final ScheduledExecutorService scannerPool;

    /**
     * 监控信息
     */
    final ScheduledExecutorService monitorPool;
    final LongAdder monitorBlockingNum;
    final LongAdder monitorEntityNum;
    final LongAdder monitorReceiveNum;
    final LongAdder monitorWorkNum;

    @SuppressWarnings("unchecked")
    public ConsumeExecutorGroup(String groupName,
                                int executorNum,
                                int executorQueueSize,
                                boolean executorSchedule,
                                EntityScanner entityScanner,
                                int monitorPeriod) {
        this.groupName = groupName;
        this.executorNum = tableSizeFor(executorNum);
        this.executorQueueSize = executorQueueSize;
        this.executorSchedule = executorSchedule;
        this.entityScanner = entityScanner;
        this.monitorPeriod = monitorPeriod;

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
        if (monitorPeriod > 0) {
            monitorBlockingNum = new LongAdder();
            monitorEntityNum = new LongAdder();
            monitorReceiveNum = new LongAdder();
            monitorWorkNum = new LongAdder();
            monitorPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, groupName + "-monitor"));
            monitorPool.scheduleAtFixedRate(() -> {
                logger.info(monitorLog());
            }, monitorPeriod, monitorPeriod, TimeUnit.SECONDS);
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
        checkClosed();
        long ts = DateUtil.CacheSecond.current() - expiredInSecond;
        for (ConsumeExecutor<T> executor : executors) {
            executor.execute(() -> {
                //double-check:进入 executor 线程后再次确认未关闭,避免在 cleanup 之后被 race 进入
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
                    removeEntity(id, executor);
                }
            });
        }
    }


    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        //1. 先停 scanner 和 monitor,避免在 executor shutdown 后还有任务提交到 executor 引发 RejectedExecutionException
        ExecutorUtil.shutdownThenAwait(true, scannerPool);

        //2. 在每个 executor 线程内清理 entity(entityMap 只能由 executor 线程访问)
        List<Future<?>> cleanups = new ArrayList<>();
        for (ConsumeExecutor<T> executor : executors) {
            cleanups.add(executor.submit(() -> {
                for (ConsumeEntity<T> entity : executor.entityMap.values()) {
                    try {
                        entity.destroy();
                    } catch (Exception ex) {
                        logger.error("entity destroy error id[{}]", entity.id, ex);
                    }
                }
                executor.entityMap.clear();
            }));
        }
        //3. 等待清理任务完成
        for (Future<?> future : cleanups) {
            try {
                future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.error("close cleanup interrupted", ex);
                break;
            } catch (ExecutionException ex) {
                logger.error("close cleanup error", ex.getCause());
            }
        }
        //4. shutdown executor 并 await 全部终止
        for (ConsumeExecutor<T> executor : executors) {
            executor.shutdown();
        }
        for (ConsumeExecutor<T> executor : executors) {
            ExecutorUtil.await(executor);
        }
        ExecutorUtil.shutdown(true, monitorPool);
    }

    /**
     * 检查是否已关闭,关闭后抛出异常让调用方感知
     */
    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("ConsumeExecutorGroup[" + groupName + "] is closed");
        }
    }

    public Future<?> removeEntity(String id) {
        checkClosed();
        ConsumeExecutor<T> executor = getExecutor(id);
        return removeEntity(id, executor);
    }

    private Future<?> removeEntity(String id, ConsumeExecutor<T> executor) {
        return executor.submit(() -> {
            //double-check:进入 executor 线程后再次确认未关闭,避免在 cleanup 之后被 race 进入
            if (closed) {
                return;
            }
            ConsumeEntity<T> remove = executor.entityMap.remove(id);
            if (remove != null) {
                try {
                    remove.destroy();
                } catch (Exception ex) {
                    logger.error("entity destroy error id[{}]", id, ex);
                }
                if (monitorPeriod > 0) {
                    monitorEntityNum.decrement();
                }
            }
        });
    }

    /**
     * 根据 func 判断是否保留 entity
     *
     * @param id   entity id
     * @param func 入参为当前 entity(可能为 null),返回 true 保留、false 销毁;返回 null 视为保留
     */
    public Future<?> checkRemoveEntity(String id, Function<ConsumeEntity<T>, Boolean> func) {
        checkClosed();
        ConsumeExecutor<T> executor = getExecutor(id);
        return executor.submit(() -> {
            //double-check:进入 executor 线程后再次确认未关闭,避免在 cleanup 之后被 race 进入
            if (closed) {
                return;
            }
            ConsumeEntity<T> entity = executor.entityMap.get(id);
            //entity 不存在则无需销毁
            if (entity == null) {
                return;
            }
            //null 或 true 都视为保留,不做销毁
            Boolean save = func.apply(entity);
            if (save == null || save) {
                return;
            }
            executor.entityMap.remove(id);
            try {
                entity.destroy();
            } catch (Exception ex) {
                logger.error("entity destroy error id[{}]", id, ex);
            }
            if (monitorPeriod > 0) {
                monitorEntityNum.decrement();
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
        checkClosed();
        ConsumeExecutor<T> executor = getExecutor(id);
        return executor.submit(() -> {
            //double-check:进入 executor 线程后再次确认未关闭
            if (closed) {
                throw new IllegalStateException("ConsumeExecutorGroup[" + groupName + "] is closed");
            }
            return executor.entityMap.get(id);
        });
    }

    public abstract ConsumeEntity<T> newEntity(String id, T first);

    public void onMessage(T t) {
        checkClosed();
        if (monitorPeriod > 0) {
            monitorBlockingNum.increment();
        }
        String id = id(t);
        ConsumeExecutor<T> executor = getExecutor(id);
        if (monitorPeriod > 0) {
            monitorReceiveNum.increment();
        }
        executor.execute(() -> {
            //double-check:进入 executor 线程后再次确认未关闭,避免在 cleanup 之后创建新 entity 却无人销毁
            if (closed) {
                if (monitorPeriod > 0) {
                    monitorBlockingNum.decrement();
                }
                return;
            }
            ConsumeEntity<T> entity = executor.entityMap.computeIfAbsent(id, k -> {
                try {
                    ConsumeEntity<T> e = newEntity(id, t);
                    e.executor = executor;
                    e.init(t);
                    if (monitorPeriod > 0) {
                        monitorEntityNum.increment();
                    }
                    return e;
                } catch (Exception ex) {
                    logger.error("consumeEntity init error groupName[{}] id[{}]", groupName, id, ex);
                    if (monitorPeriod > 0) {
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
                if (monitorPeriod > 0) {
                    monitorBlockingNum.decrement();
                    monitorWorkNum.increment();
                }
            }
        });
    }

    public String monitorLog() {
        String queueLog = Arrays.stream(executors).map(e -> String.valueOf(e.blockingQueue.size())).collect(Collectors.joining(" "));
        return StringUtil.format("consume group[{}] blockingNum[{}] entityNum[{}] receiveSpeed[{}/s] queues[{}] workSpeed[{}/s]",
                groupName,
                monitorBlockingNum.sum(),
                monitorEntityNum.sum(),
                FloatUtil.format(monitorReceiveNum.sumThenReset() / ((double) monitorPeriod), 2),
                queueLog,
                FloatUtil.format(monitorWorkNum.sumThenReset() / ((double) monitorPeriod), 2)
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
