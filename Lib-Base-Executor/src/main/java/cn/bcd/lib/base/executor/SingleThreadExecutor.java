package cn.bcd.lib.base.executor;

import cn.bcd.lib.base.exception.BaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * 单线程执行器、整合普通任务和延迟/定时任务、所有任务最终由单线程串行执行
 * 核心逻辑:
 * 1、ThreadPoolExecutor是单线程池、执行所有普通任务和转发过来的延迟/定时任务
 * 2、ScheduledThreadPoolExecutor仅接收延迟/定时任务、不执行、仅转发给 ThreadPoolExecutor
 * 3、保证所有任务串行执行、线程安全
 */
public class SingleThreadExecutor extends AbstractExecutorService implements ScheduledExecutorService {

    public final Logger logger = LoggerFactory.getLogger(SingleThreadExecutor.class);

    public final int queueSize;
    public final boolean schedule;
    public final BlockingQueue<Runnable> blockingQueue;

    //当前执行器绑定的线程
    public final Thread thread;

    // 核心执行器：单线程，执行所有实际任务（普通/延迟/定时）
    final ThreadPoolExecutor executor;

    // 调度执行器：单线程，仅接收延迟/定时任务，转发给 workerExecutor
    final ScheduledThreadPoolExecutor executor_schedule;

    /**
     * @param threadName 线程名称
     * @param queueSize  队列容量；传 0 表示无界(LinkedBlockingQueue，注意 OOM 风险)；> 0 使用 ArrayBlockingQueue
     * @param schedule   是否支持延迟/定时任务
     */
    public SingleThreadExecutor(String threadName, int queueSize, boolean schedule) {
        this.queueSize = queueSize;
        this.schedule = schedule;
        if (queueSize == 0) {
            this.blockingQueue = new LinkedBlockingQueue<>();
        } else {
            this.blockingQueue = new ArrayBlockingQueue<>(queueSize);
        }
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.SECONDS,
                this.blockingQueue,
                r -> new Thread(r, threadName),
                (r, exec) -> {
                    if (exec.isShutdown()) {
                        //shutdown 后直接抛出，避免任务被静默丢弃导致 FutureTask 永远 pending、scheduler 线程被卡住
                        throw new RejectedExecutionException("executor [" + threadName + "] is shutdown, task rejected");
                    }
                    try {
                        //队列满则阻塞入队，反压到提交方
                        exec.getQueue().put(r);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RejectedExecutionException("interrupted while waiting to enqueue task", e);
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

    private void checkScheduleEnabled() {
        if (!schedule) throw BaseException.get("scheduling not supported");
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        checkScheduleEnabled();
        return executor_schedule.schedule(() -> {
            try {
                return submit(callable).get(); // 等待核心执行器执行完成并返回结果
            } catch (InterruptedException | ExecutionException ignore) {
                return null;
            }
        }, delay, unit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        checkScheduleEnabled();
        return executor_schedule.schedule(() -> {
            submit(command); // 提交完毕即可
        }, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        checkScheduleEnabled();
        return executor_schedule.scheduleAtFixedRate(() -> {
            submit(command); // 提交完毕即可
        }, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        checkScheduleEnabled();
        return executor_schedule.scheduleWithFixedDelay(() -> {
            try {
                submit(command).get(); // 等待核心执行器执行完成并返回结果
            } catch (InterruptedException | ExecutionException ignore) {
            }
        }, initialDelay, delay, unit);
    }

    private boolean inThread() {
        return Thread.currentThread() == thread;
    }

    private Runnable safeWrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable e) {
                logger.error("task error", e);
            }
        };
    }

    private <V> Callable<V> safeWrap(Callable<V> task) {
        return () -> {
            try {
                return task.call();
            } catch (Throwable e) {
                logger.error("task error", e);
                return null;
            }
        };
    }

    private void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Throwable e) {
            logger.error("task error", e);
        }
    }

    private <V> V safeRun(Callable<V> task) {
        try {
            return task.call();
        } catch (Throwable e) {
            logger.error("task error", e);
            return null;
        }
    }

    @Override
    public void execute(Runnable task) {
        if (inThread()) {
            safeRun(task);
        } else {
            //避免任务执行异常导致线程池的线程退出、特意safeWrap
            Objects.requireNonNull(task, "task");
            executor.execute(safeWrap(task));
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (inThread()) {
            return CompletableFuture.completedFuture(safeRun(task));
        } else {
            //避免任务执行异常导致线程池的线程退出、特意safeWrap
            Objects.requireNonNull(task, "task");
            return executor.submit(safeWrap(task));
        }
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (inThread()) {
            safeRun(task);
            return CompletableFuture.completedFuture(result);
        } else {
            //避免任务执行异常导致线程池的线程退出、特意safeWrap
            Objects.requireNonNull(task, "task");
            return executor.submit(safeWrap(task), result);
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (inThread()) {
            safeRun(task);
            return CompletableFuture.completedFuture(null);
        } else {
            //避免任务执行异常导致线程池的线程退出、特意safeWrap
            Objects.requireNonNull(task, "task");
            return executor.submit(safeWrap(task));
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        List<Callable<T>> list = tasks.stream().map(this::safeWrap).toList();
        return executor.invokeAll(list);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        List<Callable<T>> list = tasks.stream().map(this::safeWrap).toList();
        return executor.invokeAll(list, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        List<Callable<T>> list = tasks.stream().map(this::safeWrap).toList();
        return executor.invokeAny(list);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        List<Callable<T>> list = tasks.stream().map(this::safeWrap).toList();
        return executor.invokeAny(list, timeout, unit);
    }

    @Override
    public void shutdown() {
        if (schedule) {
            executor_schedule.shutdown();
        }
        executor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks = new ArrayList<>();
        if (schedule) {
            tasks.addAll(executor_schedule.shutdownNow());
        }
        tasks.addAll(executor.shutdownNow());
        return tasks;
    }

    @Override
    public boolean isShutdown() {
        if (schedule) {
            return executor_schedule.isShutdown() && executor.isShutdown();
        } else {
            return executor.isShutdown();
        }
    }

    @Override
    public boolean isTerminated() {
        if (schedule) {
            return executor_schedule.isTerminated() && executor.isTerminated();
        } else {
            return executor.isTerminated();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long remainingNanos;
        if (schedule) {
            long startNanos = System.nanoTime();
            boolean schedulerTerminated = executor_schedule.awaitTermination(timeout, unit);
            if (!schedulerTerminated) {
                return false;
            }
            long elapsedNanos = System.nanoTime() - startNanos;
            remainingNanos = unit.toNanos(timeout) - elapsedNanos;
        } else {
            remainingNanos = unit.toNanos(timeout);
        }
        if (remainingNanos <= 0) {
            return false;
        }
        //等待核心执行器终止
        return executor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS);
    }
}
