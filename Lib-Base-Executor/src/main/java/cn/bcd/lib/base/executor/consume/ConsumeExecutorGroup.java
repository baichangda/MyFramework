package cn.bcd.lib.base.executor.consume;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.executor.BlockingChecker;
import cn.bcd.lib.base.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public abstract class ConsumeExecutorGroup<T> {
    Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String groupName;
    public final int executorNum;
    public final int executorQueueSize;
    public final boolean executorSchedule;
    public final BlockingChecker executorBlockingChecker;
    public final EntityScanner entityScanner;

    public ConsumeExecutor<T>[] executors;

    /**
     * 是否运行中
     */
    volatile boolean running;

    ScheduledExecutorService scannerPool;


    public ConsumeExecutorGroup(String groupName,
                                int executorNum,
                                int executorQueueSize,
                                boolean executorSchedule,
                                BlockingChecker executorBlockingChecker,
                                EntityScanner entityScanner) {
        this.groupName = groupName;
        this.executorNum = executorNum;
        this.executorQueueSize = executorQueueSize;
        this.executorSchedule = executorSchedule;
        this.executorBlockingChecker = executorBlockingChecker;
        this.entityScanner = entityScanner;
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
                    executor.removeEntityInThread(id);
                }
            });
        }
    }

    public synchronized void init() {
        if (!running) {
            running = true;
            executors = new ConsumeExecutor[executorNum];
            for (int i = 0; i < executorNum; i++) {
                executors[i] = new ConsumeExecutor<>(groupName + "-executor-" + i,
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
                            executor.removeEntityInThread(id);
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
        }

        executors = null;
    }

    public Future<?> removeEntity(String id) {
        ConsumeExecutor<T> executor = getExecutor(id);
        return executor.submit(() -> {
            executor.removeEntityInThread(id);
        });
    }

    protected ConsumeExecutor<T> getExecutor(String id) {
        return executors[Math.floorMod(id.hashCode(), executors.length)];
    }

    public abstract String id(T t);

    public abstract ConsumeEntity<T> newEntity(String id);

    public void onMessage(T t) {
        String id = id(t);
        ConsumeExecutor<T> executor = getExecutor(id);
        executor.execute(() -> {
            ConsumeEntity<T> entity = executor.entityMap.computeIfAbsent(id, k -> {
                try {
                    ConsumeEntity<T> e = newEntity(id);
                    e.executor = executor;
                    e.init();
                    return e;
                } catch (Exception ex) {
                    logger.error("consumeEntity init error groupName[{}] id[{}]", groupName, id, ex);
                    return null;
                }
            });
            if (entity != null) {
                try {
                    entity.onMessage(t);
                } catch (Exception ex) {
                    logger.error("consumeEntity onMessage error groupName[{}] id[{}]", groupName, id, ex);
                }
            }
        });
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
