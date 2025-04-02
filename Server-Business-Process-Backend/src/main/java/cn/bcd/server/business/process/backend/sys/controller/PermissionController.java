package cn.bcd.server.business.process.backend.sys.controller;

import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.database.common.condition.Condition;
import cn.bcd.lib.database.common.condition.impl.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import cn.bcd.server.business.process.backend.base.controller.BaseController;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import cn.bcd.server.business.process.backend.sys.bean.PermissionBean;
import cn.bcd.server.business.process.backend.sys.service.PermissionService;

@RestController
@RequestMapping("/api/sys/permission")
@Tag(name = "权限-PermissionController")
public class PermissionController extends BaseController {

    @Autowired
    private PermissionService permissionService;

    /**
     * 查询权限列表
     * @return
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @Operation(summary="查询权限列表")
    @ApiResponse(responseCode = "200",description = "权限列表")
    public Result<List<PermissionBean>> list(
        @Parameter(description = "id") @RequestParam(required = false) Long id,
        @Parameter(description = "权限名称") @RequestParam(required = false) String name,
        @Parameter(description = "资源") @RequestParam(required = false) String resource,
        @Parameter(description = "创建时间开始",schema = @Schema(type = "integer")) @RequestParam(required = false) Date createTimeBegin,
        @Parameter(description = "创建时间结束",schema = @Schema(type = "integer")) @RequestParam(required = false) Date createTimeEnd,
        @Parameter(description = "创建人id") @RequestParam(required = false) Long createUserId,
        @Parameter(description = "创建人姓名") @RequestParam(required = false) String createUserName,
        @Parameter(description = "更新时间开始",schema = @Schema(type = "integer")) @RequestParam(required = false) Date updateTimeBegin,
        @Parameter(description = "更新时间结束",schema = @Schema(type = "integer")) @RequestParam(required = false) Date updateTimeEnd,
        @Parameter(description = "更新人id") @RequestParam(required = false) Long updateUserId,
        @Parameter(description = "更新人姓名") @RequestParam(required = false) String updateUserName
    ){
        Condition condition= Condition.and(
           NumberCondition.EQUAL("id",id),
           StringCondition.EQUAL("name",name),
           StringCondition.EQUAL("resource",resource),
           DateCondition.BETWEEN("createTime",createTimeBegin,createTimeEnd),
           NumberCondition.EQUAL("createUserId",createUserId),
           StringCondition.EQUAL("createUserName",createUserName),
           DateCondition.BETWEEN("updateTime",updateTimeBegin,updateTimeEnd),
           NumberCondition.EQUAL("updateUserId",updateUserId),
           StringCondition.EQUAL("updateUserName",updateUserName)
        );
        return Result.success(permissionService.list(condition));
    }

    /**
     * 查询权限分页
     * @return
     */
    @RequestMapping(value = "/page", method = RequestMethod.GET)
    @Operation(summary="查询权限分页")
    @ApiResponse(responseCode = "200",description = "权限分页结果集")
    public Result<Page<PermissionBean>> page(
        @Parameter(description = "id") @RequestParam(required = false) Long id,
        @Parameter(description = "权限名称") @RequestParam(required = false) String name,
        @Parameter(description = "资源") @RequestParam(required = false) String resource,
        @Parameter(description = "创建时间开始",schema = @Schema(type = "integer")) @RequestParam(required = false) Date createTimeBegin,
        @Parameter(description = "创建时间结束",schema = @Schema(type = "integer")) @RequestParam(required = false) Date createTimeEnd,
        @Parameter(description = "创建人id") @RequestParam(required = false) Long createUserId,
        @Parameter(description = "创建人姓名") @RequestParam(required = false) String createUserName,
        @Parameter(description = "更新时间开始",schema = @Schema(type = "integer")) @RequestParam(required = false) Date updateTimeBegin,
        @Parameter(description = "更新时间结束",schema = @Schema(type = "integer")) @RequestParam(required = false) Date updateTimeEnd,
        @Parameter(description = "更新人id") @RequestParam(required = false) Long updateUserId,
        @Parameter(description = "更新人姓名") @RequestParam(required = false) String updateUserName,
        @Parameter(description = "分页参数(页数)")  @RequestParam(required = false,defaultValue = "1")Integer pageNum,
        @Parameter(description = "分页参数(页大小)") @RequestParam(required = false,defaultValue = "20") Integer pageSize
    ){
        Condition condition= Condition.and(
           NumberCondition.EQUAL("id",id),
           StringCondition.EQUAL("name",name),
           StringCondition.EQUAL("resource",resource),
           DateCondition.BETWEEN("createTime",createTimeBegin,createTimeEnd),
           NumberCondition.EQUAL("createUserId",createUserId),
           StringCondition.EQUAL("createUserName",createUserName),
           DateCondition.BETWEEN("updateTime",updateTimeBegin,updateTimeEnd),
           NumberCondition.EQUAL("updateUserId",updateUserId),
           StringCondition.EQUAL("updateUserName",updateUserName)
        );
        return Result.success(permissionService.page(condition,PageRequest.of(pageNum-1,pageSize)));
    }

    /**
     * 保存权限
     * @param permission
     * @return
     */
    @RequestMapping(value = "/save",method = RequestMethod.POST)
    @Operation(summary = "保存权限")
    @ApiResponse(responseCode = "200",description = "保存结果")
    public Result<?> save(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "权限实体") @Validated @RequestBody PermissionBean permission){
        permissionService.save(permission);
        return Result.success();
    }


    /**
     * 删除权限
     * @param ids
     * @return
     */
    @RequestMapping(value = "/delete",method = RequestMethod.DELETE)
    @Operation(summary = "删除权限")
    @ApiResponse(responseCode = "200",description = "删除结果")
    public Result<?> delete(@Parameter(description = "id数组",example = "100,101,102") @RequestParam long[] ids){
        permissionService.delete(ids);
        return Result.success();
    }

}
