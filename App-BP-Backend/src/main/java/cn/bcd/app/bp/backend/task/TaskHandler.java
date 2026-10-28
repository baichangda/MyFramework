package cn.bcd.app.bp.backend.task;

/**
 * 一个 Spring Bean 对应一种任务类型。Handler 被多个 Worker 共享，不能存放单次任务状态。
 * 单次执行和对应回调始终由同一个线程串行调用；回调异常只记日志，不改变业务执行结论。
 */
public interface TaskHandler<P> {
    /** 全集群唯一的语义标识，例如 export、report，长度不超过 64。 */
    String type();

    Class<P> payloadType();

    /** 正常返回表示成功，返回值保存到 result；停止请调用 context.checkStop()。 */
    String execute(TaskContext context, P payload) throws Exception;

    default void onStarted(TaskContext context, P payload) throws Exception {}

    default void onSucceeded(TaskContext context, P payload) throws Exception {}

    default void onFailed(TaskContext context, P payload, Exception cause) throws Exception {}

    default void onCanceled(TaskContext context, P payload) throws Exception {}

    default void onStopped(TaskContext context, P payload) throws Exception {}
}
