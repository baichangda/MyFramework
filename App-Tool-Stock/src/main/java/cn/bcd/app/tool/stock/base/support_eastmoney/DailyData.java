package cn.bcd.app.tool.stock.base.support_eastmoney;

import lombok.Data;

@Data
public class DailyData {
    //天
    public String day;
    //开盘价
    public float open;
    //收盘价
    public float close;
    //最高价
    public float highest;
    //最低价
    public float lowest;
    //成交量
    public long volume;
    //成交额
    public double amount;
    //振幅
    public float amplitude;
    //涨跌幅
    public float raiseRate;
    //涨跌额
    public float raise;
    //换手率
    public float turnover;
}
