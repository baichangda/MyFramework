package cn.bcd.app.business.process.backend.base.support_task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum StopResult {
    CANCEL_SUCCEED(0, "任务待执行、取消成功"),
    WAIT_OR_IN_EXECUTING_NOT_FOUND(1, "找不到待执行或执行中的任务"),
    IN_EXECUTING_INTERRUPT_SUCCEED(2, "任务执行中、已请求打断、等待停止"),
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
