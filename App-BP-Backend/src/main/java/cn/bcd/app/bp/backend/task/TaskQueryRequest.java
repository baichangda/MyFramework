package cn.bcd.app.bp.backend.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Date;

/** 任务筛选条件，不包含分页参数和审计信息。 */
@Getter
@Setter
public class TaskQueryRequest {
    @Schema(description = "主键")
    public Long id;

    @Schema(description = "任务名称")
    @Size(max = 50)
    public String name;

    @Schema(description = "任务状态")
    public Integer status;

    @Schema(description = "任务类型")
    @Size(max = 64)
    public String type;

    @Schema(description = "开始时间开始")
    public Date startTimeBegin;
    @Schema(description = "开始时间结束")
    public Date startTimeEnd;

    @Schema(description = "结束时间开始")
    public Date finishTimeBegin;
    @Schema(description = "结束时间结束")
    public Date finishTimeEnd;

}
