package cn.bcd.app.bp.backend.permission;

import cn.bcd.lib.base.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/permission")
@Tag(name = "权限-PermissionController")
public class PermissionController {
    private final PermissionService service;

    public PermissionController(PermissionService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @Operation(summary = "查询权限列表")
    public Result<List<PermissionResponse>> list(
            @Valid @ParameterObject @ModelAttribute PermissionQueryRequest query) {
        return Result.success(service.listPermission(query));
    }

    @GetMapping("/page")
    @Operation(summary = "查询权限分页")
    public Result<Page<PermissionResponse>> page(
            @Valid @ParameterObject @ModelAttribute PermissionQueryRequest query,
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.pagePermission(query, pageNum, pageSize));
    }

    @PostMapping("/save")
    @Operation(summary = "保存权限")
    public Result<?> save(@Valid @RequestBody PermissionSaveRequest request) {
        service.savePermission(request);
        return Result.success();
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除权限")
    public Result<?> delete(@RequestParam long[] ids) {
        service.deletePermission(ids);
        return Result.success();
    }
}
