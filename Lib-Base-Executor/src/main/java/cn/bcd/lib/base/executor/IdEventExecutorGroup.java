package cn.bcd.lib.base.executor;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.MultithreadEventExecutorGroup;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.stream.StreamSupport;

/**
 * 根据业务 ID 将任务稳定路由到固定单线程执行器的执行器组。
 * <p>
 * 相同 ID 始终返回同一个 {@link EventExecutor}，因此使用方可以在不额外加锁的情况下，
 * 串行处理同一业务对象的任务。不同 ID 可被分配到不同执行器并行处理。
 * </p>
 * <p>
 * 分片使用取模而不是位掩码，因此线程数不要求是 2 的幂。
 * </p>
 */
public class IdEventExecutorGroup extends MultithreadEventExecutorGroup {

    /**
     * 执行器快照，数组长度与实际创建的执行器数量一致。
     */
    public final EventExecutor[] executors;

    /**
     * 实际执行器数量。
     */
    public final int executorNum;

    /**
     * @param nThreads     执行器线程数，必须大于 0
     * @param threadFactory 线程工厂；传 {@code null} 时使用 Netty 默认线程工厂
     */
    public IdEventExecutorGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory, Integer.MAX_VALUE, RejectedExecutionHandlers.reject());
        this.executors = StreamSupport.stream(spliterator(), false).toArray(EventExecutor[]::new);
        this.executorNum = executors.length;
    }

    /**
     * 使用 Netty 默认线程工厂创建执行器组。
     *
     * @param nThreads 执行器线程数，必须大于 0
     */
    public IdEventExecutorGroup(int nThreads) {
        this(nThreads, null);
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
        return executors[Math.floorMod(id, executorNum)];
    }

    @Override
    protected EventExecutor newChild(Executor executor, Object... args) {
        return new DefaultEventExecutor(this, executor, (Integer) args[0], (RejectedExecutionHandler) args[1]);
    }
}
