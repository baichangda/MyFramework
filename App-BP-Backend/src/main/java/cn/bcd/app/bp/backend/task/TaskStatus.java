package cn.bcd.app.bp.backend.task;

/**
 * 任务持久化状态。WAITING 可转为 EXECUTING 或 CANCELED；
 * EXECUTING 可转为 SUCCEED、FAILED 或 STOPPED，四种终态不会自动重新入队。
 * stopRequested 仅表示停止意图，设置它不会立即改变状态。
 */
public enum TaskStatus {
    /** 已提交但尚未被任一节点领取。 */
    WAITING(1, "任务等待中"),
    /** 领取条件更新已经成功，不要求此时已进入业务方法；宕机后保留此状态人工排查。 */
    EXECUTING(2, "任务执行中"),
    /** 业务正常返回，即使最后时刻收到停止请求也按成功处理。 */
    SUCCEED(3, "任务执行成功"),
    /** 参数解析或执行过程失败。 */
    FAILED(4, "任务执行失败"),
    /** 领取前由取消方抢先从 WAITING 更新成功。 */
    CANCELED(5, "待执行的任务被取消"),
    /** 领取后由原 Worker 响应停止请求并退出。 */
    STOPPED(6, "执行中的任务被终止");

    private final int status;
    private final String name;

    TaskStatus(int status, String name) {
        this.status = status;
        this.name = name;
    }

    /** 按数据库状态码查找，未知状态返回 null。 */
    public static TaskStatus from(int status) {
        for (TaskStatus value : values()) {
            if (value.status == status) {
                return value;
            }
        }
        return null;
    }

    public int getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

}