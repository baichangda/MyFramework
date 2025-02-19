package cn.bcd.lib.base.executor;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.executor.queue.MpscArrayBlockingQueue;
import cn.bcd.lib.base.executor.queue.MpscUnboundArrayBlockingQueue;
import cn.bcd.lib.base.executor.queue.WaitStrategy;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SingleThreadExecutor extends SingleThreadEventExecutor {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String threadName;
    public final BlockingChecker blockingChecker;
    public final Consumer<SingleThreadExecutor> doBeforeExit;
    //用于通知其他线程退出
    public final CountDownLatch quitNotifier;

    /**
     * 阻塞检查器
     */
    public final ScheduledThreadPoolExecutor executor_blockingChecker;

    public SingleThreadExecutor(String threadName) {
        this(threadName, null, null);
    }

    /**
     * 构造任务执行器
     *
     * @param threadName      线程名称
     * @param blockingChecker 阻塞检查周期任务的执行周期(秒)
     *                        如果<=0则不启动阻塞检查
     *                        开启后会启动周期任务
     *                        检查逻辑为
     *                        向执行器中提交一个空任务、等待{@link BlockingChecker#maxBlockingTimeInSecond}秒后检查任务是否完成
     *                        如果没有完成则警告、且此后每{@link BlockingChecker#periodWhenBlockingInSecond}秒检查一次任务情况并警告
     * @param doBeforeExit    退出前执行的任务
     */
    public SingleThreadExecutor(String threadName,
                                BlockingChecker blockingChecker,
                                Consumer<SingleThreadExecutor> doBeforeExit) {
        super(null,
                r -> {
                    return new FastThreadLocalThread(r, threadName);
                },
                true);
        this.threadName = threadName;
        this.blockingChecker = blockingChecker;
        this.doBeforeExit = doBeforeExit;
        if (blockingChecker == null) {
            this.quitNotifier = null;
            this.executor_blockingChecker = null;
        } else {
            this.quitNotifier = new CountDownLatch(1);
            //开启阻塞监控
            int maxBlockingTimeInSecond = blockingChecker.maxBlockingTimeInSecond;
            int periodInSecond = blockingChecker.periodInSecond;
            this.executor_blockingChecker = new ScheduledThreadPoolExecutor(1, r -> new Thread(r, threadName + "-blockingChecker"));
            this.executor_blockingChecker.scheduleWithFixedDelay(() -> {
                long start = DateUtil.CacheSecond.current();
                Future<?> future = submit(() -> {
                });
                try {
                    boolean quit = quitNotifier.await(maxBlockingTimeInSecond, TimeUnit.SECONDS);
                    if (quit) {
                        return;
                    }
                    while (!future.isDone()) {
                        long blockingSecond = DateUtil.CacheSecond.current() - start;
                        if (blockingSecond >= maxBlockingTimeInSecond) {
                            logger.warn("WorkExecutor blocking threadName[{}] blockingTime[{}s>={}s] pendingTasks[{}]", threadName, blockingSecond, maxBlockingTimeInSecond, pendingTasks());
                        }
                        quit = quitNotifier.await(blockingChecker.periodWhenBlockingInSecond, TimeUnit.SECONDS);
                        if (quit) {
                            return;
                        }
                    }
                } catch (InterruptedException ex) {
                    throw BaseException.get(ex);
                }
            }, periodInSecond, periodInSecond, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void run() {
        for (; ; ) {
            Runnable task = takeTask();
            if (task != null) {
                safeExecute(task);
                updateLastExecutionTime();
            }

            if (confirmShutdown()) {
                break;
            }
        }
        if (doBeforeExit != null) {
            try {
                doBeforeExit.accept(this);
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        }
    }

    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return maxPendingTasks == Integer.MAX_VALUE
                ? new MpscUnboundArrayBlockingQueue<>(1024, WaitStrategy.PROGRESSIVE)
                : new MpscArrayBlockingQueue<>(maxPendingTasks, WaitStrategy.PROGRESSIVE);
    }

    public io.netty.util.concurrent.Future<?> shutdownGracefully() {
        return shutdownGracefully(2, Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    @Override
    public io.netty.util.concurrent.Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        if (executor_blockingChecker != null) {
            quitNotifier.countDown();
            ExecutorUtil.shutdownThenAwait(executor_blockingChecker);
        }
        return super.shutdownGracefully(quietPeriod, timeout, unit);
    }
}
