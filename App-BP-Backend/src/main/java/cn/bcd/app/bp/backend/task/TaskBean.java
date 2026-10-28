package cn.bcd.app.bp.backend.task;

import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 系统任务处理表
 */
@Getter
@Setter
@Table("t_sys_task")
public class TaskBean extends BaseBean {
    //field
    @NotBlank(message = "[任务名称]不能为空")
    @Size(max = 50, message = "[任务名称]长度不能超过50")
    @Schema(description = "任务名称", maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
    public String name;

    @NotNull(message = "[任务状态]不能为空")
    @Schema(description = "任务状态(1:等待中;2:执行中;3:执行成功;4:执行失败;5:任务被取消;6:任务被终止)", requiredMode = Schema.RequiredMode.REQUIRED)
    public int status;

    @NotBlank(message = "[任务类型]不能为空")
    @Size(max = 64, message = "[任务类型]长度不能超过64")
    @Schema(description = "任务类型，例如 export", requiredMode = Schema.RequiredMode.REQUIRED)
    public String type;

    @Schema(description = "任务参数(JSON)，由对应类型的 Handler 解析")
    public String payload;

    @Schema(description = "领取节点，用于宕机后的人工排查")
    public String nodeId;

    @Schema(description = "停止意图；执行数据更新不能覆盖此字段")
    public boolean stopRequested;

    @Size(max = 255, message = "[任务信息]长度不能超过255")
    @Schema(description = "任务信息(失败时记录失败原因)", maxLength = 255)
    public String message;

    @NotNull(message = "[任务处理进度]")
    @Schema(description = "任务处理进度", requiredMode = Schema.RequiredMode.REQUIRED)
    public float percent;

    @Schema(description = "任务开始时间")
    public Date startTime;

    @Schema(description = "任务完成时间")
    public Date finishTime;

    @Size(max = 255, message = "[任务执行结果]长度不能超过255")
    @Schema(description = "任务执行结果(不同的任务类型结果不同、记录导出任务这里存储的是导出文件在文件服务器的路径)", maxLength = 255)
    public String result;

    public TaskBean(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public TaskBean() {

    }

}
