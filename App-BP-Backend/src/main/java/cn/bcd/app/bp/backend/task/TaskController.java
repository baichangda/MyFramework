package cn.bcd.app.bp.backend.task;

import cn.bcd.lib.base.result.Result;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.DateCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NumberCondition;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;

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

/**
 * 任务记录查询和停止入口。任务提交由 Java 业务代码调用 TaskService.submit 完成，
 * HTTP 接口只查询记录和发起停止请求，业务任务由 Worker 执行。
 */
@RestController
@RequestMapping("/api/task")
@Tag(name = "任务-TaskController")
public class TaskController {

    @Autowired
    TaskService taskService;

    /**
     * 查询数据库中的任务快照，页码从 1 开始。
     * 执行进度按节流周期保存，返回值可能稍晚于 Worker 中的内存进度。
     */
    @RequestMapping(value = "/page", method = RequestMethod.GET)
    @Operation(summary = "查询任务分页")
    @ApiResponse(responseCode = "200", description = "任务分页结果集")
    public Result<Page<TaskBean>> page(
            @Parameter(description = "主键") @RequestParam(required = false) Long id,
            @Parameter(description = "任务名称") @RequestParam(required = false) String name,
            @Parameter(description = "任务状态") @RequestParam(required = false) Long status,
            @Parameter(description = "任务类型") @RequestParam(required = false) String type,
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
                StringCondition.EQUAL("type", type),
                DateCondition.BETWEEN("startTime", startTimeBegin, startTimeEnd),
                DateCondition.BETWEEN("finishTime", finishTimeBegin, finishTimeEnd),
                DateCondition.BETWEEN("createTime", createTimeBegin, createTimeEnd)
        );
        return Result.success(taskService.page(condition, PageRequest.of(pageNum - 1, pageSize)));
    }


    /**
     * 按输入顺序返回停止处理结果。执行中任务只受理停止请求，
     * 调用方应继续查询记录，以终态判断业务是否真正退出。
     */
    @RequestMapping(value = "/stop", method = RequestMethod.GET)
    @Operation(summary = "停止任务")
    @ApiResponse(responseCode = "200", description = "停止任务结果")
    public Result<StopResult[]> stop(@Parameter(description = "停止任务的主键") @RequestParam(required = true) Long[] ids) {
        StopResult[] results = taskService.stop(ids);
        return Result.success(results);
    }
}
