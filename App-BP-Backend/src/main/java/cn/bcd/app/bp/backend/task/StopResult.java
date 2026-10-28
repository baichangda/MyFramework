package cn.bcd.app.bp.backend.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum StopResult {
    CANCEL_SUCCEED(0, "任务待执行、取消成功"),
    WAIT_OR_IN_EXECUTING_NOT_FOUND(1, "找不到待执行或执行中的任务"),
    STOP_REQUESTED(2, "任务已领取、已请求停止、等待执行线程退出"),
    ;

    @JsonValue
    public final int flag;

    public final String name;

    StopResult(int flag, String name) {
        this.flag = flag;
        this.name = name;
    }

    @JsonCreator
    public static StopResult from(int flag) {
        for (StopResult value : values()) {
            if (value.flag == flag) {
                return value;
            }
        }
        return null;
    }
}
