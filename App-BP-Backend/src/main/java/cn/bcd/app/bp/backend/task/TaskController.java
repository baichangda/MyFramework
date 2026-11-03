package cn.bcd.app.bp.backend.task;

import cn.bcd.lib.base.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/** 任务快照查询和停止入口，任务提交仍由 Java 业务代码完成。 */
@RestController
@RequestMapping("/api/task")
@Tag(name = "任务-TaskController")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/page")
    @Operation(summary = "查询任务分页")
    public Result<Page<TaskResponse>> page(
            @Valid @ParameterObject @ModelAttribute TaskQueryRequest query,
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(taskService.pageTasks(query, pageNum, pageSize));
    }

    @GetMapping("/stop")
    @Operation(summary = "停止任务")
    public Result<StopResult[]> stop(@RequestParam Long[] ids) {
        return Result.success(taskService.stop(ids));
    }
}
