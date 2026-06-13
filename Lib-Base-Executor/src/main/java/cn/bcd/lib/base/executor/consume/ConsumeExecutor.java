package cn.bcd.lib.base.executor.consume;

import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.concurrent.ThreadPerTaskExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * {@link ConsumeEntity} 的单线程执行器。
 * <p>
 * 绑定到当前执行器的所有实体及其 {@link #entityMap} 只能在该执行器线程内读写，
 * 以线程封闭方式保证实体状态安全。
 * </p>
 *
 * @param <T> 实体消费的消息类型
 */
public class ConsumeExecutor<T> extends SingleThreadEventExecutor {

    /**
     * 当前执行器持有的实体。该集合不是线程安全集合，只能在执行器线程内访问。
     */
    public final Map<String, ConsumeEntity<T>> entityMap = new HashMap<>();

    /**
     * @param threadName 执行线程名称
     * @param queueSize  待执行任务队列容量；{@code 0} 表示无界队列，正数表示有界队列
     */
    public ConsumeExecutor(String threadName, int queueSize) {
        super(null,
                new ThreadPerTaskExecutor(r -> new Thread(r, threadName)),
                true,
                createTaskQueue(queueSize),
                RejectedExecutionHandlers.reject());
    }

    /**
     * 创建任务队列。有界队列满时，Netty 会按拒绝策略抛出
     * {@link java.util.concurrent.RejectedExecutionException}。
     */
    private static Queue<Runnable> createTaskQueue(int queueSize) {
        return queueSize == 0 ? new LinkedBlockingQueue<>() : new LinkedBlockingQueue<>(queueSize);
    }

    /**
     * 持续串行执行普通任务和 Netty 调度任务，直到执行器确认关闭。
     */
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
