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

public class IdEventExecutorGroup extends MultithreadEventExecutorGroup {

    public final EventExecutor[] executors;
    public final int executorNum;

    public IdEventExecutorGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory, Integer.MAX_VALUE, RejectedExecutionHandlers.reject());
        this.executors = StreamSupport.stream(spliterator(), false).toArray(EventExecutor[]::new);
        this.executorNum = executors.length;
    }

    public IdEventExecutorGroup(int nThreads) {
        this(nThreads, null);
    }

    public EventExecutor getEventExecutor(String id) {
        Objects.requireNonNull(id, "id");
        int h = id.hashCode();
        return getEventExecutor(h ^ (h >>> 16));
    }

    public EventExecutor getEventExecutor(int id) {
        return executors[Math.floorMod(id, executorNum)];
    }

    @Override
    protected EventExecutor newChild(Executor executor, Object... args) {
        return new DefaultEventExecutor(this, executor, (Integer) args[0], (RejectedExecutionHandler) args[1]);
    }
}
