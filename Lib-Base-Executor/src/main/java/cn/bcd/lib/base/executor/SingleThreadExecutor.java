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
 * <p>
 * 注意:scheduler 也是单线程,转发任务时会用 submit(...).get() 阻塞等待 worker 完成,
 * 当 worker 队列长期满载时 scheduler 会被堵住,后续定时任务堆积。
 * 使用方应合理设置 queueSize 和任务耗时,避免 worker 队列长期满载。
 */
public class SingleThreadExecutor extends AbstractExecutorService implements ScheduledExecutorService {

    public final Logger logger = LoggerFactory.getLogger(this.getClass());

    public final int queueSize;
    public final boolean schedule;
    public final BlockingQueue<Runnable> blockingQueue;

    //当前执行器绑定的线程
    public final Thread thread;

    // 核心执行器:单线程,执行所有实际任务(普通/延迟/定时)
    final ThreadPoolExecutor executor;

    // 调度执行器:单线程,仅接收延迟/定时任务,转发给 executor
    final ScheduledThreadPoolExecutor executor_schedule;

    /**
     * @param threadName 线程名称
     * @param queueSize  队列容量;传 0 表示无界(LinkedBlockingQueue,注意 OOM 风险);> 0 使用 ArrayBlockingQueue
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
                        //shutdown 后直接抛出,避免任务被静默丢弃导致 FutureTask 永远 pending、scheduler 线程被卡住
                        throw new RejectedExecutionException("executor [" + threadName + "] is shutdown, task rejected");
                    }
                    try {
                        //队列满则阻塞入队,反压到提交方
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BaseException.get(e);
        } catch (ExecutionException e) {
            throw BaseException.get(e);
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException e) {
                logger.error("scheduled task error", e);
                return null;
            }
        }, delay, unit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        checkScheduleEnabled();
        return executor_schedule.schedule(() -> {
            try {
                submit(command).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                logger.error("scheduled task error", e);
            }
        }, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        checkScheduleEnabled();
        return executor_schedule.scheduleAtFixedRate(() -> {
            try {
                submit(command).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                //捕获后让定时任务继续,避免单次失败导致整个调度链终止
                logger.error("scheduled task error", e);
            }
        }, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        checkScheduleEnabled();
        return executor_schedule.scheduleWithFixedDelay(() -> {
            try {
                submit(command).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                //捕获后让定时任务继续,避免单次失败导致整个调度链终止
                logger.error("scheduled task error", e);
            }
        }, initialDelay, delay, unit);
    }

    private boolean inThread() {
        return Thread.currentThread() == thread;
    }

    /**
     * execute 路径的安全包装:防止任务异常导致工作线程退出
     * 注意:submit 系列不使用此包装,异常通过 Future 自然传播
     */
    private Runnable safeWrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable e) {
                logger.error("task error", e);
            }
        };
    }

    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (inThread()) {
            try {
                task.run();
            } catch (Throwable e) {
                logger.error("task error", e);
            }
        } else {
            //避免任务执行异常导致线程池的线程退出、特意safeWrap
            executor.execute(safeWrap(task));
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        if (inThread()) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                future.complete(task.call());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
            return future;
        }
        return executor.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        Objects.requireNonNull(task, "task");
        if (inThread()) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                task.run();
                future.complete(result);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
            return future;
        }
        return executor.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (inThread()) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            try {
                task.run();
                future.complete(null);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
            return future;
        }
        return executor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (inThread()) {
            return runAllInline(tasks, -1L);
        }
        return executor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        if (inThread()) {
            //当前线程就是执行线程,串行执行任务并跟踪累计耗时;超过 timeout 后剩余任务标记 cancelled
            return runAllInline(tasks, unit.toNanos(timeout));
        }
        return executor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (inThread()) {
            try {
                return runAnyInline(tasks, -1L);
            } catch (TimeoutException impossible) {
                //不带 timeout 时不会抛出
                throw new ExecutionException(impossible);
            }
        }
        return executor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (inThread()) {
            //当前线程就是执行线程,串行执行;每执行完一个任务检查累计耗时,超过 timeout 抛 TimeoutException
            return runAnyInline(tasks, unit.toNanos(timeout));
        }
        return executor.invokeAny(tasks, timeout, unit);
    }

    /**
     * 在当前线程内串行执行所有任务,返回每个任务结果的 Future(成功或异常)。
     * <p>
     * timeoutNanos &lt; 0 表示无超时;&gt;= 0 时执行完每个任务后检查累计耗时,超过则剩余任务标记 cancelled。
     * 注意:无法中断已开始执行的任务,timeout 仅在任务之间生效。
     */
    private <T> List<Future<T>> runAllInline(Collection<? extends Callable<T>> tasks, long timeoutNanos) {
        long deadline = timeoutNanos < 0 ? Long.MAX_VALUE : System.nanoTime() + timeoutNanos;
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        boolean expired = false;
        for (Callable<T> task : tasks) {
            if (expired) {
                CompletableFuture<T> future = new CompletableFuture<>();
                future.cancel(false);
                futures.add(future);
                continue;
            }
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                future.complete(task.call());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
            futures.add(future);
            if (timeoutNanos >= 0 && System.nanoTime() >= deadline) {
                expired = true;
            }
        }
        return futures;
    }

    /**
     * 在当前线程内串行执行任务,返回第一个成功的结果;全部失败时抛 ExecutionException。
     * <p>
     * timeoutNanos &lt; 0 表示无超时;&gt;= 0 时执行每个任务前检查累计耗时,超过则抛 TimeoutException。
     * 注意:无法中断已开始执行的任务,timeout 仅在任务之间生效。
     */
    private <T> T runAnyInline(Collection<? extends Callable<T>> tasks, long timeoutNanos) throws ExecutionException, TimeoutException {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks is empty");
        }
        long deadline = timeoutNanos < 0 ? Long.MAX_VALUE : System.nanoTime() + timeoutNanos;
        Throwable last = null;
        for (Callable<T> task : tasks) {
            if (timeoutNanos >= 0 && System.nanoTime() >= deadline) {
                throw new TimeoutException();
            }
            try {
                return task.call();
            } catch (Throwable e) {
                last = e;
            }
        }
        throw new ExecutionException(last);
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
