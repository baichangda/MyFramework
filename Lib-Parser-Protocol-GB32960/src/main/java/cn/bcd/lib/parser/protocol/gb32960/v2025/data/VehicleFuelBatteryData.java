package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.data.NumType;
import cn.bcd.lib.parser.base.anno.data.NumVal_byte;
import cn.bcd.lib.parser.base.anno.data.NumVal_float;
import cn.bcd.lib.parser.base.anno.data.NumVal_short;

/**
 * 燃料电池数据
 */
public class VehicleFuelBatteryData {

    //车载氢系统中最高温度
    @F_num(type = NumType.uint16, valExpr = "x-40")
    public NumVal_short maxTemperature;

    //车载氢系统中最中最高温度探针代号
    @F_num(type = NumType.uint8)
    public NumVal_short maxTemperatureCode;

    //氢气最高浓度
    @F_num(type = NumType.uint16, valExpr = "x/10000")
    public NumVal_float maxConcentration;

    //氢气最高浓度传感器代号
    @F_num(type = NumType.uint8)
    public NumVal_short maxConcentrationCode;

    //氢气最高压力
    @F_num(type = NumType.uint16, valExpr = "x/10")
    public NumVal_float maxPressure;

    //氢气最高压力传感器代号
    @F_num(type = NumType.uint8)
    public NumVal_short maxPressureCode;

    //高压DC/DC状态
    @F_num(type = NumType.uint8)
    public NumVal_byte dcStatus;

    //剩余氢量百分比
    @F_num(type = NumType.uint8)
    public NumVal_byte percent;

    //高压 DC/DC控制器温度
    @F_num(type = NumType.uint8, valExpr = "x-40")
    public NumVal_short dcTemperature;

}
