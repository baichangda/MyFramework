package cn.bcd.app.bp.backend.task;

import java.util.concurrent.TimeUnit;

/** 单次执行上下文。除 requestStop 外，所有方法和 task 字段只允许所属 Worker 使用。 */
public final class TaskContext {
    private final TaskBean task;
    private final TaskService taskService;
    private volatile boolean stopRequested;
    private long lastProgressNanos;

    TaskContext(TaskBean task, TaskService taskService) {
        this.task = task;
        this.taskService = taskService;
    }

    public TaskBean task() {
        return task;
    }

    /** 广播线程只设置标记，不修改任务实体或调用生命周期回调。 */
    void requestStop() {
        stopRequested = true;
    }

    public void checkStop() {
        if (stopRequested) {
            throw new TaskStopException();
        }
    }

    /** 业务循环主动调用，同线程最多每秒保存一次；终态保存会补齐最后一次进度。 */
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
