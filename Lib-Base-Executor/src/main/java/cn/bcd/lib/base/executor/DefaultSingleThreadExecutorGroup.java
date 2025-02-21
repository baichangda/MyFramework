package cn.bcd.lib.base.executor;

public class DefaultSingleThreadExecutorGroup extends SingleThreadExecutorGroup<SingleThreadExecutor> {
    public DefaultSingleThreadExecutorGroup(String groupName, int executorNum, int executorQueueSize, boolean executorSchedule, BlockingChecker executorBlockingChecker) {
        super(groupName, executorNum, executorQueueSize, executorSchedule, executorBlockingChecker);
    }

    public DefaultSingleThreadExecutorGroup(String groupName) {
        this(groupName, Runtime.getRuntime().availableProcessors(), 0, false, BlockingChecker.DEFAULT);
    }

    @Override
    protected SingleThreadExecutor newExecutor(int index) {
        return new SingleThreadExecutor(
                groupName + "-executor(" + (index + 1) + "/" + executorNum + ")",
                executorQueueSize,
                executorSchedule,
                executorBlockingChecker);
    }
}
