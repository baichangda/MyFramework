package cn.bcd.lib.base.executor.consume;

import io.netty.util.concurrent.SingleThreadEventExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

public class ConsumeExecutor<T> extends SingleThreadEventExecutor {

    public final Map<String, ConsumeEntity<T>> entityMap = new HashMap<>();

    public ConsumeExecutor(String threadName) {
        super(null, (ThreadFactory) r -> new Thread(r, threadName), true);
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
