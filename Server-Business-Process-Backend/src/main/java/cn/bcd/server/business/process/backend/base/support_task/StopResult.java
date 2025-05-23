package cn.bcd.server.business.process.backend.base.support_task;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum StopResult {
    CANCEL_SUCCEED(0, "任务取消成功"),
    WAIT_OR_IN_EXECUTING_NOT_FOUND(1, "找不到待执行或执行中的任务"),
    IN_EXECUTING_INTERRUPT_NOT_SUPPORT(2, "任务执行中、不支持打断"),
    IN_EXECUTING_INTERRUPT_SUCCEED(3, "任务执行中、已请求打断、等待停止"),
    ;

    public final int flag;

    public final String name;

    StopResult(int flag, String name) {
        this.flag = flag;
        this.name = name;
    }

    public static StopResult from(int flag) {
        for (StopResult value : values()) {
            if (value.flag == flag) {
                return value;
            }
        }
        return null;
    }
}
