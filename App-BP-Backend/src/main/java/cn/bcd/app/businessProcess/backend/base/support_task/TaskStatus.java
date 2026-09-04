package cn.bcd.app.businessProcess.backend.base.support_task;

public enum TaskStatus {
    WAITING(1, "任务等待中"),
    EXECUTING(2, "任务执行中"),
    SUCCEED(3, "任务执行成功"),
    FAILED(4, "任务执行失败"),
    CANCELED(5, "待执行的任务被取消"),
    STOPPED(6, "执行中的任务被终止");

    private final int status;
    private final String name;

    TaskStatus(int status, String name) {
        this.status = status;
        this.name = name;
    }

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