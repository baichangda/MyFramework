package cn.bcd.app.bp.backend.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 停止接口的处理结果，与任务的持久化状态 TaskStatus 不同。
 * STOP_REQUESTED 只表示已提出停止请求，实际退出时间取决于业务检查点。
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum StopResult {
    /** 已成功将等待任务更新为 CANCELED。 */
    CANCEL_SUCCEED(0, "任务待执行、取消成功"),
    /** 记录不存在，或者检查时任务已经结束。 */
    WAIT_OR_IN_EXECUTING_NOT_FOUND(1, "找不到待执行或执行中的任务"),
    /** 已保存执行中任务的停止意图并发送通知，尚未确认业务退出。 */
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
    /** 按接口结果编码还原，未知编码返回 null。 */
    public static StopResult from(int flag) {
        for (StopResult value : values()) {
            if (value.flag == flag) {
                return value;
            }
        }
        return null;
    }
}
