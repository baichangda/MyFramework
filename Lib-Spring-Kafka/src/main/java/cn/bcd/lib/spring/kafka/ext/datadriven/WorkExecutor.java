package cn.bcd.lib.spring.kafka.ext.datadriven;

import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 * 一个工作线程和一个任务队列，串行处理分配到本执行器的所有 ID。
 * Handler 状态及 Map 的访问应始终放在本执行器中，线程外访问仍可能产生数据竞争。
 * 避免长期阻塞任务：同一执行器上的其他 ID、过期扫描和退出清理都会被一起延迟。
 */
public class WorkExecutor extends SingleThreadEventExecutor {
    private static final Logger logger = LoggerFactory.getLogger(WorkExecutor.class);

    /**
     * 只由所属工作线程访问，因此使用 HashMap 而非并发 Map；null ID 也可作为 key。
     */
    public final Map<String, WorkHandler> workHandlers = new HashMap<>();

    public WorkExecutor(ThreadFactory threadFactory) {
        this(null, threadFactory);
    }

    private WorkExecutor(EventExecutorGroup parent, ThreadFactory threadFactory) {
        super(parent, threadFactory, true);
    }

    /** 只在所属线程中调用；先移除再销毁，避免重复或重入销毁同一个 Handler。 */
    void destroyHandler(String id) {
        WorkHandler handler = workHandlers.remove(id);
        if (handler != null) {
            try {
                handler.destroy();
            } catch (Exception ex) {
                logger.error("workHandler destroy error id[{}]", handler.id, ex);
            } finally {
                if (handler.consumer != null && handler.consumer.monitor_period > 0) {
                    handler.consumer.monitor_workHandlerCount.decrement();
                }
            }
        }
    }

    /** 执行器正常关闭或异常退出时，在所属线程内销毁剩余 Handler。 */
    @Override
    protected void cleanup() {
        try {
            for (String id : new ArrayList<>(workHandlers.keySet())) {
                destroyHandler(id);
            }
        } finally {
            workHandlers.clear();
            super.cleanup();
        }
    }

    @Override
    protected void run() {
        for (;;) {
            // 无任务时等待队列或关闭信号，避免空转占用 CPU。
            Runnable task = takeTask();
            if (task != null) {
                runTask(task);
                // Netty 的优雅关闭依赖最后执行时间判断任务活动状态。
                updateLastExecutionTime();
            }

            // 关闭流程还需要运行待处理任务和 shutdown hook，不能仅检查关闭标志就退出。
            if (confirmShutdown()) {
                break;
            }
        }
    }
}
