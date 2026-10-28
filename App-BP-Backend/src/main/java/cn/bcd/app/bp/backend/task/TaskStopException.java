package cn.bcd.app.bp.backend.task;

/** 业务线程在停止检查点抛出，由 Worker 转换为 STOPPED。 */
public final class TaskStopException extends RuntimeException {
    public TaskStopException() {
        super("任务收到停止请求", null, false, false);
    }
}
