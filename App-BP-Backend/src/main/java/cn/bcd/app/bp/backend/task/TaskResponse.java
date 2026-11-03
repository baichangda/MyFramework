package cn.bcd.app.bp.backend.task;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Date;

/** 任务接口返回模型，与数据库实体独立。 */
public class TaskResponse {
    @Schema(description = "主键")
    public Long id;

    @Schema(description = "任务名称")
    public String name;

    @Schema(description = "任务状态")
    public Integer status;

    @Schema(description = "任务类型")
    public String type;

    @Schema(description = "任务参数(JSON)")
    public String payload;

    @Schema(description = "领取节点")
    public String nodeId;

    @Schema(description = "停止意图")
    public Boolean stopRequested;

    @Schema(description = "任务信息")
    public String message;

    @Schema(description = "任务进度")
    public Float percent;

    @Schema(description = "开始时间")
    public Date startTime;

    @Schema(description = "结束时间")
    public Date finishTime;

    @Schema(description = "任务结果")
    public String result;

    @Schema(description = "创建时间")
    public Date createTime;

    @Schema(description = "创建人id")
    public Long createUserId;

    @Schema(description = "创建人姓名")
    public String createUserName;

    @Schema(description = "更新时间")
    public Date updateTime;

    @Schema(description = "更新人id")
    public Long updateUserId;

    @Schema(description = "更新人姓名")
    public String updateUserName;

}
