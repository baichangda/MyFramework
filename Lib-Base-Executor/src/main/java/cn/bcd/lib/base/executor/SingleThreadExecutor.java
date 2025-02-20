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

public class SingleThreadExecutor {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String threadName;
    public final int queueSize;
    public final boolean schedule;
    public final BlockingChecker blockingChecker;

    public BlockingQueue<Runnable> blockingQueue;
    //用于通知其他线程退出
    public CountDownLatch quitNotifier;

    /**
     * 任务执行器
     */
    public ThreadPoolExecutor executor;
    /**
     * 计划任务执行器
     */
    public ScheduledThreadPoolExecutor executor_schedule;
    /**
     * 阻塞检查器
     */
    public ScheduledThreadPoolExecutor executor_blockingChecker;

    /**
     * 当前执行器绑定的线程
     */
    public Thread thread;

    volatile boolean running;

    volatile CompletableFuture<?> destroyFuture;

    /**
     * @param threadName      线程名称
     *                        最多存在3个线程、名称分别如下
     *                        threadName 工作线程
     *                        threadName-schedule 计划任务线程
     *                        threadName-blockingChecker 阻塞检查线程
     * @param queueSize       队列长度
     *                        0则表示无边界
     * @param schedule        是否开启计划任务功能
     * @param blockingChecker 是否开启阻塞检查
     */
    public SingleThreadExecutor(String threadName,
                                int queueSize,
                                boolean schedule,
                                BlockingChecker blockingChecker) {
        this.threadName = threadName;
        this.queueSize = queueSize;
        this.schedule = schedule;
        this.blockingChecker = blockingChecker;

    }

    public synchronized void init() {
        if (!running) {
            running = true;
            try {
                destroyFuture = null;
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
                thread = executor.submit(Thread::currentThread).get();

                if (schedule) {
                    executor_schedule = new ScheduledThreadPoolExecutor(1, r -> new Thread(r, threadName + "-schedule"));
                }

                if (blockingChecker != null) {
                    quitNotifier = new CountDownLatch(1);
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
                }
            } catch (Exception ex) {
                destroy();
                throw BaseException.get(ex);
            }
        }
    }

    public synchronized Future<?> destroy() {
        return destroy(null);
    }

    public synchronized CompletableFuture<?> destroy(Runnable doBeforeExit) {
        if (running) {
            running = false;
            //开启新的线程执行销毁
            try (ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, threadName + "-destroy"))) {
                destroyFuture = CompletableFuture.runAsync(() -> {
                    try {
                        if (doBeforeExit != null) {
                            executor.submit(doBeforeExit).get();
                        }
                        //通知退出
                        if (quitNotifier != null) {
                            quitNotifier.countDown();
                        }
                        ExecutorUtil.shutdownAllThenAwait(executor, executor_schedule, executor_blockingChecker);
                        //清空变量
                        blockingQueue = null;
                        quitNotifier = null;
                        executor = null;
                        executor_schedule = null;
                        executor_blockingChecker = null;
                        thread = null;
                    } catch (Exception ex) {
                        logger.error("error", ex);
                    }
                }, executorService);
                return destroyFuture;
            }
        } else {
            return destroyFuture;
        }
    }

    public boolean inThread() {
        return Thread.currentThread() == thread;
    }

    private void checkRunning() {
        if (!running) {
            throw BaseException.get("executor[{}] not running", threadName);
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
