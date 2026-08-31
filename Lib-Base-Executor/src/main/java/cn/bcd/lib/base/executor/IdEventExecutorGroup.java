package cn.bcd.lib.base.executor;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 根据业务 ID 将任务稳定路由到固定单线程执行器的执行器分配器。
 * <p>
 * 相同 ID 始终返回同一个 {@link EventExecutor}，因此使用方可以在不额外加锁的情况下，
 * 串行处理同一业务对象的任务。不同 ID 可被分配到不同执行器并行处理。
 * </p>
 * <p>
 * 实际线程数会向上取整为 2 的幂，例如 {@code 3 -> 4}、{@code 5 -> 8}，
 * 从而可以通过位掩码快速完成分片。
 * </p>
 */
public class IdEventExecutorGroup implements AutoCloseable {

    /**
     * 执行器数组，只通过 ID 分配方法向调用方提供其中的执行器。
     */
    private final EventExecutor[] executors;

    public IdEventExecutorGroup() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * @param nThreads 期望执行器线程数，必须大于 0；实际数量向上取整为 2 的幂
     */
    public IdEventExecutorGroup(int nThreads) {
        int executorNum = tableSizeFor(nThreads);
        executors = new EventExecutor[executorNum];
        for (int i = 0; i < executorNum; i++) {
            executors[i] = Objects.requireNonNull(
                    newEventExecutor(i),
                    "newEventExecutor returned null");
        }
    }

    /**
     * 创建指定分片使用的执行器。子类可以覆盖此方法，自行决定线程工厂、
     * 任务队列和拒绝策略等执行器配置。
     * <p>
     * 此方法在父类构造期间调用，覆盖实现不能依赖子类构造函数中初始化的实例字段。
     * </p>
     *
     * @param index 分片索引，从 {@code 0} 开始
     * @return 新的执行器，不能为 {@code null}
     */
    protected EventExecutor newEventExecutor(int index) {
        return new DefaultEventExecutor();
    }

    private static int tableSizeFor(int cap) {
        if (cap <= 0 || cap > (1 << 30)) {
            throw new IllegalArgumentException("nThreads must be between 1 and " + (1 << 30));
        }
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return n < 0 ? 1 : n + 1;
    }

    /**
     * 根据字符串 ID 的散列值选择执行器。
     *
     * @param id 业务 ID，不能为 {@code null}
     * @return 该 ID 固定对应的单线程执行器
     */
    public EventExecutor getEventExecutor(String id) {
        Objects.requireNonNull(id, "id");
        int h = id.hashCode();
        return getEventExecutor(h ^ (h >>> 16));
    }

    /**
     * 根据整数 ID 选择执行器，支持负数及 {@link Integer#MIN_VALUE}。
     *
     * @param id 业务 ID
     * @return 该 ID 固定对应的单线程执行器
     */
    public EventExecutor getEventExecutor(int id) {
        return executors[id & (executors.length - 1)];
    }

    /**
     * 在 ID 对应的单线程执行器中执行任务。
     *
     * @param id      业务 ID，不能为 {@code null}
     * @param command 要执行的任务
     */
    public void execute(String id, Runnable command) {
        Objects.requireNonNull(command, "command");
        getEventExecutor(id).execute(command);
    }

    /**
     * 在 ID 对应的单线程执行器中提交任务。
     *
     * @param id      业务 ID，不能为 {@code null}
     * @param command 要提交的任务
     * @return 用于等待任务完成或取消任务的 Future
     */
    public Future<?> submit(String id, Runnable command) {
        Objects.requireNonNull(command, "command");
        return getEventExecutor(id).submit(command);
    }

    /**
     * 在 ID 对应的单线程执行器中提交有返回值的任务。
     *
     * @param id   业务 ID，不能为 {@code null}
     * @param task 要提交的任务
     * @param <T>  任务结果类型
     * @return 用于获取任务结果或取消任务的 Future
     */
    public <T> Future<T> submit(String id, Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return getEventExecutor(id).submit(task);
    }

    /**
     * 在给定延迟后，在 ID 对应的单线程执行器中执行一次任务。
     */
    public ScheduledFuture<?> schedule(String id, Runnable command, long delay, TimeUnit unit) {
        Objects.requireNonNull(command, "command");
        return getEventExecutor(id).schedule(command, delay, unit);
    }

    /**
     * 在给定延迟后，在 ID 对应的单线程执行器中执行一次有返回值的任务。
     *
     * @param id       业务 ID，不能为 {@code null}
     * @param callable 要执行的任务
     * @param delay    延迟时间
     * @param unit     延迟时间单位
     * @param <V>      任务结果类型
     * @return 用于获取任务结果或取消任务的 ScheduledFuture
     */
    public <V> ScheduledFuture<V> schedule(String id, Callable<V> callable, long delay, TimeUnit unit) {
        Objects.requireNonNull(callable, "callable");
        return getEventExecutor(id).schedule(callable, delay, unit);
    }

    /**
     * 在 ID 对应的单线程执行器中，以固定频率重复执行任务。
     */
    public ScheduledFuture<?> scheduleAtFixedRate(String id, Runnable command,
                                                  long initialDelay, long period, TimeUnit unit) {
        Objects.requireNonNull(command, "command");
        return getEventExecutor(id).scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    /**
     * 在 ID 对应的单线程执行器中，以固定间隔重复执行任务。
     * 间隔从上一次任务执行完成后开始计算。
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(String id, Runnable command,
                                                     long initialDelay, long delay, TimeUnit unit) {
        Objects.requireNonNull(command, "command");
        return getEventExecutor(id).scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    /**
     * 优雅关闭全部执行器，并等待已有任务执行完成。
     * 从某个内部执行器线程调用时，不等待该执行器自身终止，以避免死锁。
     */
    @Override
    public void close() {
        for (EventExecutor executor : executors) {
            executor.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        for (EventExecutor executor : executors) {
            if (!executor.inEventLoop()) {
                executor.terminationFuture().syncUninterruptibly();
            }
        }
    }
}
