package cn.bcd.app.businessProcess.openapi.controller;

import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.database.common.condition.Condition;
import cn.bcd.lib.database.common.condition.impl.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import cn.bcd.app.businessProcess.openapi.bean.OpenapiUserBean;
import cn.bcd.app.businessProcess.openapi.service.OpenapiUserService;

@RestController
@RequestMapping("/api/openapi/openapiUser")
@Tag(name = "openapi用户-OpenapiUserController")
public class OpenapiUserController{

    @Autowired
    private OpenapiUserService openapiUserService;

    /**
     * 查询openapi用户列表
     * @return
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @Operation(summary="查询openapi用户列表")
    @ApiResponse(responseCode = "200",description = "openapi用户列表")
    public Result<List<OpenapiUserBean>> list(
        @Parameter(description = "主键") @RequestParam(required = false) Long id,
        @Parameter(description = "用户名") @RequestParam(required = false) String name,
        @Parameter(description = "用户状态(1:启用;0:禁用)") @RequestParam(required = false) Integer status,
        @Parameter(description = "密钥") @RequestParam(required = false) String secretKey,
        @Parameter(description = "备注") @RequestParam(required = false) String remark,
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
           NumberCondition.EQUAL("status",status),
           StringCondition.EQUAL("secretKey",secretKey),
           StringCondition.EQUAL("remark",remark),
           DateCondition.BETWEEN("createTime",createTimeBegin,createTimeEnd),
           NumberCondition.EQUAL("createUserId",createUserId),
           StringCondition.EQUAL("createUserName",createUserName),
           DateCondition.BETWEEN("updateTime",updateTimeBegin,updateTimeEnd),
           NumberCondition.EQUAL("updateUserId",updateUserId),
           StringCondition.EQUAL("updateUserName",updateUserName)
        );
        return Result.success(openapiUserService.list(condition));
    }

    /**
     * 查询openapi用户分页
     * @return
     */
    @RequestMapping(value = "/page", method = RequestMethod.GET)
    @Operation(summary="查询openapi用户分页")
    @ApiResponse(responseCode = "200",description = "openapi用户分页结果集")
    public Result<Page<OpenapiUserBean>> page(
        @Parameter(description = "主键") @RequestParam(required = false) Long id,
        @Parameter(description = "用户名") @RequestParam(required = false) String name,
        @Parameter(description = "用户状态(1:启用;0:禁用)") @RequestParam(required = false) Integer status,
        @Parameter(description = "密钥") @RequestParam(required = false) String secretKey,
        @Parameter(description = "备注") @RequestParam(required = false) String remark,
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
           NumberCondition.EQUAL("status",status),
           StringCondition.EQUAL("secretKey",secretKey),
           StringCondition.EQUAL("remark",remark),
           DateCondition.BETWEEN("createTime",createTimeBegin,createTimeEnd),
           NumberCondition.EQUAL("createUserId",createUserId),
           StringCondition.EQUAL("createUserName",createUserName),
           DateCondition.BETWEEN("updateTime",updateTimeBegin,updateTimeEnd),
           NumberCondition.EQUAL("updateUserId",updateUserId),
           StringCondition.EQUAL("updateUserName",updateUserName)
        );
        return Result.success(openapiUserService.page(condition,PageRequest.of(pageNum-1,pageSize)));
    }

    /**
     * 保存openapi用户
     * @param openapiUser
     * @return
     */
    @RequestMapping(value = "/save",method = RequestMethod.POST)
    @Operation(summary = "保存openapi用户")
    @ApiResponse(responseCode = "200",description = "保存结果")
    public Result<?> save(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "openapi用户实体") @Validated @RequestBody OpenapiUserBean openapiUser){
        openapiUserService.save(openapiUser);
        return Result.success();
    }


    /**
     * 删除openapi用户
     * @param ids
     * @return
     */
    @RequestMapping(value = "/delete",method = RequestMethod.DELETE)
    @Operation(summary = "删除openapi用户")
    @ApiResponse(responseCode = "200",description = "删除结果")
    public Result<?> delete(@Parameter(description = "id数组",example = "100,101,102") @RequestParam long[] ids){
        openapiUserService.delete(ids);
        return Result.success();
    }

}
