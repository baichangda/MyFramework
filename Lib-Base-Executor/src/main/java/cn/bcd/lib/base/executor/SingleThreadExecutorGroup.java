package cn.bcd.lib.base.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleThreadExecutorGroup implements AutoCloseable {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String groupName;
    public final int executorNum;
    public final int executorQueueSize;
    public final boolean executorSchedule;
    public final SingleThreadExecutor[] executors;

    boolean closed;

    public SingleThreadExecutorGroup(String groupName, int executorNum, int executorQueueSize, boolean executorSchedule) {
        this.groupName = groupName;
        this.executorNum = executorNum;
        this.executorQueueSize = executorQueueSize;
        this.executorSchedule = executorSchedule;
        this.executors = new SingleThreadExecutor[executorNum];
        for (int i = 0; i < executorNum; i++) {
            executors[i] = newExecutor(i);
        }
    }


    @Override
    public synchronized void close() throws Exception {
        if (!closed) {
            closed = true;
            for (SingleThreadExecutor executor : executors) {
                executor.close();
            }
        }
    }

    protected SingleThreadExecutor newExecutor(int index) {
        return new SingleThreadExecutor(
                groupName + "-executor(" + (index + 1) + "/" + executorNum + ")",
                executorQueueSize,
                executorSchedule);
    }


    @SuppressWarnings("unchecked")
    public SingleThreadExecutor getExecutor(String id) {
        return executors[Math.floorMod(id.hashCode(), executorNum)];
    }
}
