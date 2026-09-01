package cn.bcd.lib.base.executor.consume;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    static Logger logger = LoggerFactory.getLogger(ConsumeExecutor.class);

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
        for (; ; ) {
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

    /**
     * 执行器线程退出前销毁其持有的全部实体。
     * <p>
     * {@link SingleThreadEventExecutor} 会在执行线程的退出阶段调用此方法，因此实体的
     * 销毁仍然遵守线程封闭约束，也不需要向可能已经满的任务队列提交清理任务。
     * </p>
     */
    @Override
    protected void cleanup() {
        try {
            for (ConsumeEntity<T> entity : entityMap.values()) {
                try {
                    entity.destroy();
                } catch (Exception ex) {
                    logger.error("entity destroy error id[{}]", entity.id, ex);
                }
            }
        } finally {
            entityMap.clear();
            try {
                super.cleanup();
            } catch (Exception ex) {
                logger.error("executor cleanup error", ex);
            }
        }
    }

    /**
     * 发起优雅关闭。外部线程等待执行器完全退出；执行器自身线程不能等待自己，因而只发起关闭。
     */
    @Override
    public void close() {
        Future<?> termination = shutdownGracefully(0, 5, java.util.concurrent.TimeUnit.SECONDS);
        if (!inEventLoop()) {
            termination.syncUninterruptibly();
        }
    }

}
