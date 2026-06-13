package cn.bcd.lib.base.executor.consume;

import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.concurrent.ThreadPerTaskExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class ConsumeExecutor<T> extends SingleThreadEventExecutor {

    public final Map<String, ConsumeEntity<T>> entityMap = new HashMap<>();

    public ConsumeExecutor(String threadName, int queueSize) {
        super(null,
                new ThreadPerTaskExecutor(r -> new Thread(r, threadName)),
                true,
                createTaskQueue(queueSize),
                RejectedExecutionHandlers.reject());
    }

    private static Queue<Runnable> createTaskQueue(int queueSize) {
        return queueSize == 0 ? new LinkedBlockingQueue<>() : new LinkedBlockingQueue<>(queueSize);
    }

    @Override
    protected void run() {
        for (;;) {
            Runnable task = takeTask();
            if (task != null) {
                runTask(task);
                updateLastExecutionTime();
            }
            if (confirmShutdown()) {
                break;
            }
        }
    }
}
