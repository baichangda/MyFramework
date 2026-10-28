package cn.bcd.app.bp.backend.task;

/**
 * 一个 Spring Bean 对应一种任务类型。Handler 被多个 Worker 共享，不能存放单次任务状态。
 * 单次执行和对应回调由同一个 Worker 串行调用；等待任务的取消回调由取消请求线程调用。
 * 回调异常只记日志，不改变业务执行结论。回调可设置 result、message，不应自行修改框架状态。
 *
 * @param <P> 仅包含业务参数的 JSON DTO；查询结果、Service 等运行时对象不能放入其中。
 */
public interface TaskHandler<P> {
    /** 全集群唯一的语义标识，例如 export、report，长度不超过 64。 */
    String type();

    /** 用于提交时校验和领取后反序列化；泛型参数应使用明确的 DTO 类。 */
    Class<P> payloadType();

    /**
     * 在领取任务的 Worker 中执行。正常返回即成功，结果允许为 null。
     * 长循环应主动调用 context.checkStop()，数据库查询、上传等阻塞操作不会被强制打断。
     *
     * @param context 当前任务的执行上下文，不能交给其他线程修改任务数据
     * @param payload 从数据库还原的业务参数
     * @return 保存到任务 result 的结果，例如导出文件路径
     * @throws TaskStopException 在检查点响应停止请求，Worker 将任务保存为 STOPPED
     * @throws Exception 其他业务异常，Worker 将任务保存为 FAILED
     */
    String execute(TaskContext context, P payload) throws Exception;

    /** 数据库已是 EXECUTING、业务尚未执行时调用；若提前收到停止请求，可能跳过本回调。 */
    default void onStarted(TaskContext context, P payload) throws Exception {}

    /** 业务正常返回后、终态保存前调用；此时内存中已设置结果、成功状态和完成时间。 */
    default void onSucceeded(TaskContext context, P payload) throws Exception {}

    /** 执行失败后、终态保存前调用；参数无法反序列化时不会调用本回调。 */
    default void onFailed(TaskContext context, P payload, Exception cause) throws Exception {}

    /** 等待任务取消落库后，由取消请求线程调用；不会与该任务的 Worker 并发执行。 */
    default void onCanceled(TaskContext context, P payload) throws Exception {}

    /** 业务响应停止请求退出后、STOPPED 落库前，由原 Worker 调用。 */
    default void onStopped(TaskContext context, P payload) throws Exception {}
}
