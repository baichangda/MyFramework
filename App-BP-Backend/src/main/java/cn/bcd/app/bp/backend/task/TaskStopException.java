package cn.bcd.app.bp.backend.task;

/**
 * 协作停止的控制信号，由 TaskContext.checkStop() 抛出，Worker 单独捕获并保存 STOPPED。
 * 它不表示执行失败；关闭堆栈采集，避免为正常的停止流程构造异常堆栈。
 */
public final class TaskStopException extends RuntimeException {
    public TaskStopException() {
        super("任务收到停止请求", null, false, false);
    }
}
