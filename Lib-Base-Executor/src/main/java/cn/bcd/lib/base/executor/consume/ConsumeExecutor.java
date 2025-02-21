package cn.bcd.lib.base.executor.consume;

import cn.bcd.lib.base.executor.BlockingChecker;
import cn.bcd.lib.base.executor.SingleThreadExecutor;

import java.util.HashMap;
import java.util.Map;

public class ConsumeExecutor<T> extends SingleThreadExecutor {

    public final Map<String, ConsumeEntity<T>> entityMap = new HashMap<>();

    public ConsumeExecutor(String threadName,
                           int queueSize,
                           boolean schedule,
                           BlockingChecker blockingChecker) {
        super(threadName, queueSize, schedule, blockingChecker);
    }


}
