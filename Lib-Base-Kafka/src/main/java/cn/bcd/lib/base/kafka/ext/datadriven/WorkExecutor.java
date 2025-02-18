package cn.bcd.lib.base.kafka.ext.datadriven;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 工作执行器
 * 注意:
 * 非阻塞任务线程中的任务不能阻塞、且任务之间是串行执行的、没有线程安全问题
 */
public class WorkExecutor extends SingleThreadEventExecutor {

    static Logger logger = LoggerFactory.getLogger(WorkExecutor.class);

    public final String threadName;

    public final BlockingChecker blockingChecker;

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
        super(null,
                r -> {
                    return new FastThreadLocalThread(r, threadName);
                },
                true);
        this.threadName = threadName;
        this.blockingChecker = blockingChecker;
    }

    @Override
    protected void run() {
        for (; ; ) {
            Runnable task = takeTask();
            if (task != null) {
                runTask(task);
                updateLastExecutionTime();
            }

            if (confirmShutdown()) {
                break;
            }
        }
    }

    public void init() {
        if (blockingChecker != null) {
            //开启阻塞监控
            int expiredInSecond = blockingChecker.expiredInSecond;
            int periodInSecond = blockingChecker.periodInSecond;
            this.executor_blockingChecker = new ScheduledThreadPoolExecutor(1, r -> new Thread(r, threadName + "-blockingChecker"));
            this.executor_blockingChecker.scheduleWithFixedDelay(() -> {
                long start = DateUtil.CacheSecond.current();
                Future<?> future = submit(() -> {
                });
                try {
                    TimeUnit.SECONDS.sleep(expiredInSecond);
                    while (!future.isDone()) {
                        long blockingSecond = DateUtil.CacheSecond.current() - start;
                        if (blockingSecond >= expiredInSecond) {
                            logger.warn("WorkExecutor blocking threadName[{}] blockingTime[{}s>={}s] pendingTasks[{}]", threadName, blockingSecond, expiredInSecond, pendingTasks());
                        }
                        TimeUnit.SECONDS.sleep(3);
                    }
                } catch (InterruptedException ex) {
                    throw BaseException.get(ex);
                }
            }, periodInSecond, periodInSecond, TimeUnit.SECONDS);
        }
    }

    public void destroy() {
        try {
            io.netty.util.concurrent.Future<?> future = shutdownGracefully(2, Long.MAX_VALUE, TimeUnit.SECONDS);
            executor_blockingChecker.shutdown();
            future.await();
            ExecutorUtil.await(executor_blockingChecker);
        } catch (InterruptedException e) {
            throw BaseException.get(e);
        }
        this.executor_blockingChecker = null;
    }
}
