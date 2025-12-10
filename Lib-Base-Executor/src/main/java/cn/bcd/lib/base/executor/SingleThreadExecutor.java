package cn.bcd.lib.base.executor;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.executor.queue.MpscArrayBlockingQueue;
import cn.bcd.lib.base.executor.queue.MpscUnboundArrayBlockingQueue;
import cn.bcd.lib.base.executor.queue.WaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * 单线程执行器、整合普通任务和延迟/定时任务、所有任务最终由单线程串行执行
 * 核心逻辑:
 * 1、ThreadPoolExecutor是单线程池、执行所有普通任务和转发过来的延迟/定时任务
 * 2、ScheduledThreadPoolExecutor仅接收延迟/定时任务、不执行、仅转发给 ThreadPoolExecutor
 * 3、保证所有任务串行执行、线程安全
 */
public class SingleThreadExecutor extends AbstractExecutorService implements ScheduledExecutorService {

    public final Logger logger = LoggerFactory.getLogger(this.getClass());

    public final int queueSize;
    public final boolean schedule;
    public BlockingQueue<Runnable> blockingQueue;

    //当前执行器绑定的线程
    public Thread thread;

    // 核心执行器：单线程，执行所有实际任务（普通/延迟/定时）
    final ThreadPoolExecutor executor;

    // 调度执行器：单线程，仅接收延迟/定时任务，转发给 workerExecutor
    final ScheduledThreadPoolExecutor executor_schedule;

    public SingleThreadExecutor(String threadName, int queueSize, boolean schedule) {
        this.queueSize = queueSize;
        this.schedule = schedule;
        if (queueSize == 0) {
            this.blockingQueue = new MpscUnboundArrayBlockingQueue<>(1024, WaitStrategy.PROGRESSIVE_100MS);
        } else {
            this.blockingQueue = new MpscArrayBlockingQueue<>(queueSize, WaitStrategy.PROGRESSIVE_100MS);
        }
        this.executor = new ThreadPoolExecutor(
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

        if (schedule) {
            executor_schedule = new ScheduledThreadPoolExecutor(1, r -> new Thread(r, threadName + "-schedule"));
        } else {
            executor_schedule = null;
        }

        //获取当前线程
        try {
            thread = executor.submit(Thread::currentThread).get();
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        if (schedule) {
            return executor_schedule.schedule(() -> {
                try {
                    return submit(callable).get(); // 等待核心执行器执行完成并返回结果
                } catch (InterruptedException | ExecutionException ignore) {
                    return null;
                }
            }, delay, unit);
        } else {
            throw BaseException.get("not support");
        }
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        if (schedule) {
            return executor_schedule.schedule(() -> {
                try {
                    submit(command).get(); // 等待核心执行器执行完成并返回结果
                } catch (InterruptedException | ExecutionException ignore) {
                }
            }, delay, unit);
        } else {
            throw BaseException.get("not support");
        }
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (schedule) {
            return executor_schedule.scheduleAtFixedRate(() -> {
                try {
                    submit(command).get(); // 等待核心执行器执行完成并返回结果
                } catch (InterruptedException | ExecutionException ignore) {
                }
            }, initialDelay, period, unit);
        } else {
            throw BaseException.get("not support");
        }
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (schedule) {
            return executor_schedule.scheduleWithFixedDelay(() -> {
                try {
                    submit(command).get(); // 等待核心执行器执行完成并返回结果
                } catch (InterruptedException | ExecutionException ignore) {
                }
            }, initialDelay, delay, unit);
        } else {
            throw BaseException.get("not support");
        }
    }

    private boolean inThread() {
        return Thread.currentThread() == thread;
    }

    private Runnable safeWrap(Runnable command) {
        return () -> {
            try {
                command.run();
            } catch (Exception e) {
                logger.error("command error", e);
            }
        };
    }

    private <T> Callable<T> safeWrap(Callable<T> task) {
        return () -> {
            try {
                return task.call();
            } catch (Exception e) {
                logger.error("task error", e);
                return null;
            }
        };
    }

    @Override
    public void execute(Runnable command) {
        if (inThread()) {
            command.run();
        } else {
            executor.execute(safeWrap(command));
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (inThread()) {
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception e) {
                throw BaseException.get(e);
            }
        } else {
            return executor.submit(safeWrap(task));
        }
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return executor.submit(safeWrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return executor.submit(safeWrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        //不用包装、本质上调用的execute方法
        return executor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        //不用包装、本质上调用的execute方法
        return executor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        //不用包装、本质上调用的execute方法
        return executor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        //不用包装、本质上调用的execute方法
        return executor.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void shutdown() {
        executor_schedule.shutdown();
        executor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> schedulerTasks = executor_schedule.shutdownNow();
        List<Runnable> workerTasks = executor.shutdownNow();
        schedulerTasks.addAll(workerTasks);
        return schedulerTasks;
    }

    @Override
    public boolean isShutdown() {
        return executor_schedule.isShutdown() && executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executor_schedule.isTerminated() && executor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long startNanos = System.nanoTime();
        boolean schedulerTerminated = executor_schedule.awaitTermination(timeout, unit);
        if (!schedulerTerminated) {
            return false;
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        long remainingNanos = unit.toNanos(timeout) - elapsedNanos;
        if (remainingNanos <= 0) {
            return false;
        }
        //等待核心执行器终止
        return executor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS);
    }
}