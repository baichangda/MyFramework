package cn.bcd.app.tool.stock.business.controller;

import cn.bcd.app.tool.stock.business.service.CashFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tool/stock/cashFlow")
public class CashFlowController {

    @Autowired
    CashFlowService cashFlowService;

    @RequestMapping(value = "/collectCashFlowToday", method = RequestMethod.GET)
    @Operation(summary = "采集当前资金流向")
    @ApiResponse(responseCode = "200")
    public void collectCashFlowToday(@RequestParam(required = false) @Parameter(description = "股票代码、以,分割、不传则使用定时任务中的股票代码") String codes) {
        if (codes == null) {
            cashFlowService.collectCashFlowToday();
        } else {
            cashFlowService.collectCashFlowToday(codes.split(","));
        }
    }
}
