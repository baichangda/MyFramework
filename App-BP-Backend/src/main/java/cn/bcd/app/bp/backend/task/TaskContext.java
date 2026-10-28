package cn.bcd.app.bp.backend.task;

import java.util.concurrent.TimeUnit;

/**
 * 单次任务的线程内上下文。执行任务由其 Worker 使用，取消回调由取消请求线程使用。
 * 仅 requestStop 允许跨线程调用；实体修改、进度保存和生命周期回调必须串行。
 */
public final class TaskContext {
    private final TaskBean task;
    private final TaskService taskService;
    /** 外部线程只写此标记，volatile 保证业务线程在检查点能看见请求。 */
    private volatile boolean stopRequested;
    /** 使用单调时钟计算保存间隔，避免系统时间调整影响节流。 */
    private long lastProgressNanos;

    TaskContext(TaskBean task, TaskService taskService) {
        this.task = task;
        this.taskService = taskService;
    }

    /** 当前线程持有的任务实体，可设置业务结果和消息；不要修改 ID、状态或停止意图。 */
    public TaskBean task() {
        return task;
    }

    /** 广播线程只设置标记，不修改任务实体或调用生命周期回调。 */
    void requestStop() {
        stopRequested = true;
    }

    /**
     * 仅检查本机标记，不查询数据库、不发起 Redis 请求。
     * 业务应让停止异常向上退出，交给 Worker 完成资源收尾后的状态保存。
     */
    public void checkStop() {
        if (stopRequested) {
            throw new TaskStopException();
        }
    }

    /**
     * 立即更新内存进度，首次调用保存，之后最多每秒写库一次；终态保存会补齐最后的进度。
     * 不启动额外定时线程，调用方应在业务循环中主动调用。
     *
     * @param percent 有限数值，范围为 0 到 100
     */
    public void updateProgress(float percent) {
        if (!Float.isFinite(percent) || percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be between 0 and 100");
        }
        task.percent = percent;
        long now = System.nanoTime();
        if (lastProgressNanos == 0 || now - lastProgressNanos >= TimeUnit.SECONDS.toNanos(1)) {
            taskService.saveProgress(task);
            lastProgressNanos = now;
        }
    }
}
