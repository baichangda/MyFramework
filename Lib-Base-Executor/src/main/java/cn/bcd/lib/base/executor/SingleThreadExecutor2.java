package cn.bcd.lib.base.executor;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.executor.queue.MpscArrayBlockingQueue;
import cn.bcd.lib.base.executor.queue.MpscUnboundArrayBlockingQueue;
import cn.bcd.lib.base.executor.queue.WaitStrategy;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class SingleThreadExecutor2 {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String threadName;
    public final int queueSize;
    public final boolean schedule;
    public final BlockingQueue<Runnable> blockingQueue;
    public final BlockingChecker blockingChecker;
    //用于通知其他线程退出
    public final CountDownLatch quitNotifier = new CountDownLatch(1);

    /**
     * 任务执行器
     */
    public final ThreadPoolExecutor executor;
    /**
     * 计划任务执行器
     */
    public final ScheduledThreadPoolExecutor executor_schedule;
    /**
     * 阻塞检查器
     */
    public final ScheduledThreadPoolExecutor executor_blockingChecker;

    /**
     * 当前执行器绑定的线程
     */
    public final Thread thread;

    /**
     * 0、正在运行
     * 1、停止中
     * 2、已停止
     */
    volatile int status = 0;

    public final Consumer<SingleThreadExecutor2> doBeforeShutdown;

    public SingleThreadExecutor2(String threadName, int queueSize, boolean schedule, BlockingChecker blockingChecker, Consumer<SingleThreadExecutor2> doBeforeShutdown) {
        this.threadName = threadName;
        this.queueSize = queueSize;
        this.schedule = schedule;
        this.blockingChecker = blockingChecker;
        this.doBeforeShutdown = doBeforeShutdown;
        if (queueSize == 0) {
            this.blockingQueue = new MpscUnboundArrayBlockingQueue<>(1024, WaitStrategy.PROGRESSIVE);
        } else {
            this.blockingQueue = new MpscArrayBlockingQueue<>(queueSize, WaitStrategy.PROGRESSIVE);
        }
        executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.SECONDS,
                this.blockingQueue,
                r -> new Thread(r, threadName),
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

        //获取当前线程
        try {
            thread = executor.submit(Thread::currentThread).get();
        } catch (InterruptedException | ExecutionException e) {
            throw BaseException.get(e);
        }

        if (schedule) {
            executor_schedule = new ScheduledThreadPoolExecutor(1, r -> new Thread(r, threadName + "-schedule"));
        } else {
            executor_schedule = null;
        }

        if (blockingChecker != null) {
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
                    TimeUnit.SECONDS.sleep(maxBlockingTimeInSecond);
                    while (!future.isDone()) {
                        long blockingSecond = DateUtil.CacheSecond.current() - start;
                        if (blockingSecond >= maxBlockingTimeInSecond) {
                            logger.warn("WorkExecutor blocking threadName[{}] blockingTime[{}s>={}s] queueSize[{}]", threadName, blockingSecond, maxBlockingTimeInSecond, executor.getQueue().size());
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
        } else {
            this.executor_blockingChecker = null;
        }
    }

    private void doBeforeShutdown() {
        if (doBeforeShutdown != null) {
            //回调
            SingleThreadExecutor2 temp = this;
            if (inThread()) {
                doBeforeShutdown.accept(temp);
            } else {
                try {
                    executor.submit(() -> {
                        doBeforeShutdown.accept(temp);
                    }).get();
                } catch (InterruptedException | ExecutionException ex) {
                    logger.error("error", ex);
                }
            }
        }
    }

    public void shutdown() {
        if (status == 0) {
            synchronized (this) {
                if (status == 0) {
                    status = 1;
                    doBeforeShutdown();
                    //通知退出
                    quitNotifier.countDown();
                    ExecutorUtil.shutdownAllThenAwait(executor, executor_schedule, executor_blockingChecker);
                    status = 2;
                }
            }
        }
    }

    public boolean inThread() {
        return Thread.currentThread() == thread;
    }

    private void checkRunning() {
        if (status > 0) {
            throw BaseException.get("executor[{}] status[{}]", threadName, status);
        }
    }

    private void checkSchedule() {
        if (!schedule) {
            throw BaseException.get("executor[{}] not support schedule", threadName);
        }
    }

    public final void execute(Runnable runnable) {
        checkRunning();
        if (inThread()) {
            runnable.run();
        } else {
            executor.execute(runnable);
        }
    }

    public final Future<?> submit(Runnable runnable) {
        checkRunning();
        if (inThread()) {
            FutureTask<?> futureTask = new FutureTask<>(runnable, null);
            futureTask.run();
            return futureTask;
        } else {
            return executor.submit(runnable);
        }
    }

    public final <T> Future<T> submit(Runnable runnable, T task) {
        checkRunning();
        if (inThread()) {
            FutureTask<T> futureTask = new FutureTask<>(runnable, task);
            futureTask.run();
            return futureTask;
        } else {
            return executor.submit(runnable, task);
        }
    }

    public final <T> Future<T> submit(Callable<T> task) {
        checkRunning();
        if (inThread()) {
            FutureTask<T> futureTask = new FutureTask<>(task);
            futureTask.run();
            return futureTask;
        } else {
            return executor.submit(task);
        }
    }

    public final ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        checkRunning();
        checkSchedule();
        return executor_schedule.schedule(() -> submit(command).get(), delay, unit);
    }

    public final <T> ScheduledFuture<T> schedule(Callable<T> callable, long delay, TimeUnit unit) {
        checkRunning();
        checkSchedule();
        return executor_schedule.schedule(() -> submit(callable).get(), delay, unit);
    }

    public final ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        checkRunning();
        checkSchedule();
        return executor_schedule.scheduleAtFixedRate(() -> {
            try {
                submit(command).get();
            } catch (InterruptedException | ExecutionException e) {
                throw BaseException.get(e);
            }
        }, initialDelay, period, unit);
    }

    public final ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long period, TimeUnit unit) {
        checkRunning();
        checkSchedule();
        return executor_schedule.scheduleWithFixedDelay(() -> {
            try {
                submit(command).get();
            } catch (InterruptedException | ExecutionException e) {
                throw BaseException.get(e);
            }
        }, initialDelay, period, unit);
    }
}
