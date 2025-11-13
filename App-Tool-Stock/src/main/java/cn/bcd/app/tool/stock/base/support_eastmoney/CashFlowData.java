package cn.bcd.app.tool.stock.base.support_eastmoney;

import lombok.Data;

/**
 * 资金流向
 */
@Data
public class CashFlowData {
    public String minute;
    //主力净流入
    public double d1;
    //超大单净流入
    public double d2;
    //大单净流入
    public double d3;
    //中单净流入
    public double d4;
    //小单净流入
    public double d5;
}
