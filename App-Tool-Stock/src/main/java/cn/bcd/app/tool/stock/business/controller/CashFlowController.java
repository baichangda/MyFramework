package cn.bcd.app.tool.stock.business.controller;

import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.*;
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
import cn.bcd.app.tool.stock.business.bean.CashFlowBean;
import cn.bcd.app.tool.stock.business.service.CashFlowService;

@RestController
@RequestMapping("/api/business/cashFlow")
@Tag(name = "资金流向-CashFlowController")
public class CashFlowController{

    @Autowired
    private CashFlowService cashFlowService;

    /**
     * 查询资金流向列表
     * @return
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @Operation(summary="查询资金流向列表")
    @ApiResponse(responseCode = "200",description = "资金流向列表")
    public Result<List<CashFlowBean>> list(
        @Parameter(description = "") @RequestParam(required = false) Long id,
        @Parameter(description = "股票号码") @RequestParam(required = false) String code,
        @Parameter(description = "分钟时间") @RequestParam(required = false) String minute,
        @Parameter(description = "主力净流入") @RequestParam(required = false) Double d1,
        @Parameter(description = "超大单净流入") @RequestParam(required = false) Double d2,
        @Parameter(description = "大单净流入") @RequestParam(required = false) Double d3,
        @Parameter(description = "中单净流入") @RequestParam(required = false) Double d4,
        @Parameter(description = "小单净流入") @RequestParam(required = false) Double d5
    ){
        Condition condition= Condition.and(
           NumberCondition.EQUAL("id",id),
           StringCondition.EQUAL("code",code),
           StringCondition.EQUAL("minute",minute),
           NumberCondition.EQUAL("d1",d1),
           NumberCondition.EQUAL("d2",d2),
           NumberCondition.EQUAL("d3",d3),
           NumberCondition.EQUAL("d4",d4),
           NumberCondition.EQUAL("d5",d5)
        );
        return Result.success(cashFlowService.list(condition));
    }

    /**
     * 查询资金流向分页
     * @return
     */
    @RequestMapping(value = "/page", method = RequestMethod.GET)
    @Operation(summary="查询资金流向分页")
    @ApiResponse(responseCode = "200",description = "资金流向分页结果集")
    public Result<Page<CashFlowBean>> page(
        @Parameter(description = "") @RequestParam(required = false) Long id,
        @Parameter(description = "股票号码") @RequestParam(required = false) String code,
        @Parameter(description = "分钟时间") @RequestParam(required = false) String minute,
        @Parameter(description = "主力净流入") @RequestParam(required = false) Double d1,
        @Parameter(description = "超大单净流入") @RequestParam(required = false) Double d2,
        @Parameter(description = "大单净流入") @RequestParam(required = false) Double d3,
        @Parameter(description = "中单净流入") @RequestParam(required = false) Double d4,
        @Parameter(description = "小单净流入") @RequestParam(required = false) Double d5,
        @Parameter(description = "分页参数(页数)")  @RequestParam(required = false,defaultValue = "1")Integer pageNum,
        @Parameter(description = "分页参数(页大小)") @RequestParam(required = false,defaultValue = "20") Integer pageSize
    ){
        Condition condition= Condition.and(
           NumberCondition.EQUAL("id",id),
           StringCondition.EQUAL("code",code),
           StringCondition.EQUAL("minute",minute),
           NumberCondition.EQUAL("d1",d1),
           NumberCondition.EQUAL("d2",d2),
           NumberCondition.EQUAL("d3",d3),
           NumberCondition.EQUAL("d4",d4),
           NumberCondition.EQUAL("d5",d5)
        );
        return Result.success(cashFlowService.page(condition,PageRequest.of(pageNum-1,pageSize)));
    }

    /**
     * 保存资金流向
     * @param cashFlow
     * @return
     */
    @RequestMapping(value = "/save",method = RequestMethod.POST)
    @Operation(summary = "保存资金流向")
    @ApiResponse(responseCode = "200",description = "保存结果")
    public Result<?> save(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "资金流向实体") @Validated @RequestBody CashFlowBean cashFlow){
        cashFlowService.save(cashFlow);
        return Result.success();
    }


    /**
     * 删除资金流向
     * @param ids
     * @return
     */
    @RequestMapping(value = "/delete",method = RequestMethod.DELETE)
    @Operation(summary = "删除资金流向")
    @ApiResponse(responseCode = "200",description = "删除结果")
    public Result<?> delete(@Parameter(description = "id数组",example = "100,101,102") @RequestParam long[] ids){
        cashFlowService.delete(ids);
        return Result.success();
    }

}
