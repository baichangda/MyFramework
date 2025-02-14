package cn.bcd.base.kafka.ext.datadriven;

import cn.bcd.base.exception.BaseException;
import cn.bcd.base.util.DateUtil;
import cn.bcd.base.util.ExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 工作执行器
 * 通过两个线程执行不同的任务
 * <p>
 * - {@link #executor}执行非阻塞任务
 * <p>
 * 注意:
 * 非阻塞任务线程中的任务不能阻塞、且任务之间是串行执行的、没有线程安全问题
 */
public class WorkExecutor {

    static Logger logger = LoggerFactory.getLogger(WorkExecutor.class);

    public final String threadName;

    public final BlockingChecker blockingChecker;

    /**
     * 任务执行器
     */
    public ScheduledThreadPoolExecutor executor;

    /**
     * 阻塞检查器
     */
    public ScheduledThreadPoolExecutor executor_blockingChecker;

    /**
     * 存储本执行器所有的handler
     */
    public final Map<String, WorkHandler> workHandlers = new HashMap<>();


    public static final class BlockingChecker {
        public final int periodInSecond;
        public final int expiredInSecond;

        public BlockingChecker(int periodInSecond, int expiredInSecond) {
            this.periodInSecond = periodInSecond;
            this.expiredInSecond = expiredInSecond;
        }

        /**
         * @param periodInSecond  定时任务周期(秒)
         * @param expiredInSecond 判断阻塞时间(秒)
         */
        public static BlockingChecker get(int periodInSecond, int expiredInSecond) {
            return new BlockingChecker(periodInSecond, expiredInSecond);
        }
    }

    /**
     * 构造任务执行器
     *
     * @param threadName      线程名称
     * @param blockingChecker 阻塞检查周期任务的执行周期(秒)
     *                        如果<=0则不启动阻塞检查
     *                        开启后会启动周期任务
     *                        检查逻辑为
     *                        向执行器中提交一个空任务、等待{@link BlockingChecker#expiredInSecond}秒后检查任务是否完成、如果没有完成则警告、且此后每一秒检查一次任务情况并警告
     */
    public WorkExecutor(String threadName, BlockingChecker blockingChecker) {
        this.threadName = threadName;
        this.blockingChecker = blockingChecker;
    }

    public final void execute(Runnable runnable) {
        executor.execute(runnable);
    }

    public final Future<?> submit(Runnable runnable) {
        return executor.submit(runnable);
    }

    public final <T> Future<T> submit(Runnable runnable, T task) {
        return executor.submit(runnable, task);
    }

    public final <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    public final ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return executor.schedule(command, delay, unit);
    }

    public final <T> ScheduledFuture<T> schedule(Callable<T> callable, long delay, TimeUnit unit) {
        return executor.schedule(callable, delay, unit);
    }

    public final ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return executor.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    public final ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return executor.scheduleWithFixedDelay(command, initialDelay, period, unit);
    }

    public void init() {
        this.executor = new ScheduledThreadPoolExecutor(1, r -> new Thread(r, threadName),
                (r, executor) -> {
                    if (!executor.isShutdown()) {
                        try {
//                    logger.warn("workThread[{}] RejectedExecutionHandler",threadName);
                            executor.getQueue().put(r);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });

        if (blockingChecker != null) {
            //开启阻塞监控
            int expiredInSecond = blockingChecker.expiredInSecond;
            int periodInSecond = blockingChecker.periodInSecond;
            this.executor_blockingChecker = new ScheduledThreadPoolExecutor(1, r -> new Thread(r, threadName + "-blockingChecker"));
            this.executor_blockingChecker.scheduleWithFixedDelay(() -> {
                long expired = DateUtil.CacheMillisecond.current() + expiredInSecond;
                Future<?> future = submit(() -> {
                });
                try {
                    TimeUnit.SECONDS.sleep(expiredInSecond);
                    while (!future.isDone()) {
                        long blockingSecond = DateUtil.CacheMillisecond.current() - expired;
                        if (blockingSecond >= 0) {
                            logger.warn("WorkExecutor blocking threadName[{}] blockingTime[{}s>={}s] queueSize[{}]", threadName, blockingSecond + expiredInSecond, expiredInSecond, executor.getQueue().size());
                        }
                        TimeUnit.SECONDS.sleep(1);
                    }
                } catch (InterruptedException ex) {
                    throw BaseException.get(ex);
                }
            }, periodInSecond, periodInSecond, TimeUnit.SECONDS);
        }
    }

    public void destroy() {
        ExecutorUtil.shutdownAllThenAwait(executor, executor_blockingChecker);
        this.executor = null;
        this.executor_blockingChecker = null;
    }
}
