package cn.bcd.lib.base.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleThreadExecutorGroup implements AutoCloseable {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String groupName;
    public final int executorNum;
    public final int executorQueueSize;
    public final boolean executorSchedule;
    public final SingleThreadExecutor[] executors;

    boolean closed;

    /**
     *
     * @param groupName
     * @param executorNum       最好是2的倍数、如果不是则向上取整2的倍数
     * @param executorQueueSize
     * @param executorSchedule
     */
    public SingleThreadExecutorGroup(String groupName, int executorNum, int executorQueueSize, boolean executorSchedule) {
        this.groupName = groupName;
        this.executorNum = tableSizeFor(executorNum);
        this.executorQueueSize = executorQueueSize;
        this.executorSchedule = executorSchedule;
        this.executors = new SingleThreadExecutor[this.executorNum];
        for (int i = 0; i < this.executorNum; i++) {
            executors[i] = newExecutor(i);
        }
    }

    private static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : n + 1;
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
        int h = id.hashCode();
        h = h ^ (h >>> 16);
        return executors[h & (executorNum - 1)];
    }
}
