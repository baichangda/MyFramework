package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumVal_float;
import cn.bcd.lib.parser.base.data.NumVal_int;
import cn.bcd.lib.parser.base.data.NumVal_short;

/**
 * 超级电容极值数据
 */
public class VehicleSupercapacitorLimitValueData {
    //最高电压管理系统号
    @F_num(type = NumType.uint8)
    public NumVal_short maxVoltageSystemNo;

    //最高电压超级电容单体代号
    @F_num(type = NumType.uint16)
    public NumVal_int maxVoltageCode;

    //超级电容单体电压最高值
    @F_num(type = NumType.uint16,  valExpr = "x/1000")
    public NumVal_float maxVoltage;

    //最低电压管理系统号
    @F_num(type = NumType.uint8)
    public NumVal_short minVoltageSystemNo;

    //最低电压超级电容单体代号
    @F_num(type = NumType.uint16)
    public NumVal_int minVoltageCode;

    //超级电容单体电压最低值
    @F_num(type = NumType.uint16,  valExpr = "x/1000")
    public NumVal_float minVoltage;

    //最高温度管理系统号
    @F_num(type = NumType.uint8)
    public NumVal_short maxTemperatureSystemNo;

    //最高温度探针代号
    @F_num(type = NumType.uint16)
    public NumVal_int maxTemperatureNo;

    //最高温度值
    @F_num(type = NumType.uint8,  valExpr = "x-40")
    public NumVal_short maxTemperature;

    //最低温度管理系统号
    @F_num(type = NumType.uint8)
    public NumVal_short minTemperatureSystemNo;

    //最低温度探针代号
    @F_num(type = NumType.uint16)
    public NumVal_int minTemperatureNo;

    //最低温度值
    @F_num(type = NumType.uint8,  valExpr = "x-40")
    public NumVal_short minTemperature;
}
