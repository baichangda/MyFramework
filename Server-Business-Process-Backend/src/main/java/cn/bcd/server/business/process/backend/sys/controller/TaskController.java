package cn.bcd.server.business.process.backend.sys.controller;

import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.database.common.condition.Condition;
import cn.bcd.lib.database.common.condition.impl.DateCondition;
import cn.bcd.lib.database.common.condition.impl.NumberCondition;
import cn.bcd.lib.database.common.condition.impl.StringCondition;
import cn.bcd.server.business.process.backend.base.support_task.StopResult;
import cn.bcd.server.business.process.backend.sys.bean.TaskBean;
import cn.bcd.server.business.process.backend.sys.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequestMapping("/api/sys/task")
@Tag(name = "任务-TaskController")
public class TaskController {

    @Autowired
    TaskService taskService;

    /**
     * 查询任务分页
     *
     * @return
     */
    @RequestMapping(value = "/page", method = RequestMethod.GET)
    @Operation(summary = "查询任务分页")
    @ApiResponse(responseCode = "200", description = "任务分页结果集")
    public Result<Page<TaskBean>> page(
            @Parameter(description = "主键") @RequestParam(required = false) Long id,
            @Parameter(description = "任务名称") @RequestParam(required = false) String name,
            @Parameter(description = "任务状态") @RequestParam(required = false) Long status,
            @Parameter(description = "任务类型") @RequestParam(required = false) Long type,
            @Parameter(description = "开始时间开始", schema = @Schema(type = "integer")) @RequestParam(required = false) Date startTimeBegin,
            @Parameter(description = "开始时间结束", schema = @Schema(type = "integer")) @RequestParam(required = false) Date startTimeEnd,
            @Parameter(description = "结束时间开始", schema = @Schema(type = "integer")) @RequestParam(required = false) Date finishTimeBegin,
            @Parameter(description = "结束时间结束", schema = @Schema(type = "integer")) @RequestParam(required = false) Date finishTimeEnd,
            @Parameter(description = "创建时间开始", schema = @Schema(type = "integer")) @RequestParam(required = false) Date createTimeBegin,
            @Parameter(description = "创建时间结束", schema = @Schema(type = "integer")) @RequestParam(required = false) Date createTimeEnd,
            @Parameter(description = "分页参数(页数)") @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @Parameter(description = "分页参数(页大小)") @RequestParam(required = false, defaultValue = "20") Integer pageSize
    ) {
        Condition condition = Condition.and(
                NumberCondition.EQUAL("id", id),
                StringCondition.ALL_LIKE("name", name),
                NumberCondition.EQUAL("status", status),
                NumberCondition.EQUAL("type", type),
                DateCondition.BETWEEN("startTime", startTimeBegin, startTimeEnd),
                DateCondition.BETWEEN("finishTime", finishTimeBegin, finishTimeEnd),
                DateCondition.BETWEEN("createTime", createTimeBegin, createTimeEnd)
        );
        return Result.success(taskService.page(condition, PageRequest.of(pageNum - 1, pageSize)));
    }


    @RequestMapping(value = "/stop", method = RequestMethod.GET)
    @Operation(summary = "停止任务")
    @ApiResponse(responseCode = "200", description = "停止任务结果")
    public Result<StopResult[]> stop(@Parameter(description = "停止任务的主键") @RequestParam(required = true) Long[] ids) {
        StopResult[] results = taskService.taskBuilder.stop(ids);
        return Result.success(results);
    }
}
