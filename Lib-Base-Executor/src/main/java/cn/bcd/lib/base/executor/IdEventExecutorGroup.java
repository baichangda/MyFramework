package cn.bcd.lib.base.executor;


import io.netty.util.concurrent.*;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.stream.StreamSupport;

public class IdEventExecutorGroup extends MultithreadEventExecutorGroup {

    public final EventExecutor[] executors;
    public final int executorNum;

    public IdEventExecutorGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
        this.executorNum = tableSizeFor(nThreads);
        this.executors = StreamSupport.stream(spliterator(), false).toArray(EventExecutor[]::new);
    }

    public IdEventExecutorGroup(int nThreads) {
        this(nThreads, null);
    }

    private static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : n + 1;
    }

    public EventExecutor getEventExecutor(String id) {
        int h = id.hashCode();
        h = h ^ (h >>> 16);
        return getEventExecutor(h);
    }

    public EventExecutor getEventExecutor(int id) {
        return executors[id & (executorNum - 1)];
    }

    @Override
    protected EventExecutor newChild(Executor executor, Object... args) throws Exception {
        return new DefaultEventExecutor(this, executor, (Integer) args[0], (RejectedExecutionHandler) args[1]);
    }
}
