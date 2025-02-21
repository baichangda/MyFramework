package cn.bcd.lib.base.executor;

import cn.bcd.lib.base.exception.BaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public abstract class SingleThreadExecutorGroup{
    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String groupName;
    public final int executorNum;
    public final int executorQueueSize;
    public final boolean executorSchedule;
    public final BlockingChecker executorBlockingChecker;

    public SingleThreadExecutor[] executors;

    volatile boolean running;

    public SingleThreadExecutorGroup(String groupName, int executorNum, int executorQueueSize, boolean executorSchedule, BlockingChecker executorBlockingChecker) {
        this.groupName = groupName;
        this.executorNum = executorNum;
        this.executorQueueSize = executorQueueSize;
        this.executorSchedule = executorSchedule;
        this.executorBlockingChecker = executorBlockingChecker;
    }

    public synchronized void init() {
        if (!running) {
            running = true;
            executors = new SingleThreadExecutor[executorNum];
            for (int i = 0; i < executorNum; i++) {
                executors[i] = newExecutor(i);
                executors[i].init();
            }
        }
    }

    public synchronized void destroy() {
        if (running) {
            running = false;
            //静默1s用于任务提交
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                logger.error("error", e);
            }
            List<CompletableFuture<?>> futureList = new ArrayList<>();
            for (SingleThreadExecutor executor : executors) {
                futureList.add(executor.destroy());
            }
            for (CompletableFuture<?> future : futureList) {
                future.join();
            }
            executors = null;
        }
    }

    private void checkRunning() {
        if (!running) {
            throw BaseException.get("executorGroup[{}] not running", groupName);
        }
    }

    protected SingleThreadExecutor newExecutor(int index) {
        return new SingleThreadExecutor(
                groupName + "-executor(" + (index + 1) + "/" + executorNum + ")",
                executorQueueSize,
                executorSchedule,
                executorBlockingChecker);
    }


    @SuppressWarnings("unchecked")
    public SingleThreadExecutor getExecutor(String id) {
        checkRunning();
        return executors[Math.floorMod(id.hashCode(), executorNum)];
    }
}
