package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 燃料电池数据
 */
public class VehicleFuelBatteryData {

    //车载氢系统中最高温度
    @F_num(type = NumType.uint16, valExpr = "x-40", checkVal = true)
    public short maxTemperature;
    public byte maxTemperature__v;

    //车载氢系统中最中最高温度探针代号
    @F_num(type = NumType.uint8, checkVal = true)
    public short maxTemperatureCode;
    public byte maxTemperatureCode__v;

    //氢气最高浓度
    @F_num(type = NumType.uint16, valExpr = "x/10000", checkVal = true)
    public float maxConcentration;
    public byte maxConcentration__v;

    //氢气最高浓度传感器代号
    @F_num(type = NumType.uint8, checkVal = true)
    public short maxConcentrationCode;
    public byte maxConcentrationCode__v;

    //氢气最高压力
    @F_num(type = NumType.uint16, valExpr = "x/10", checkVal = true)
    public float maxPressure;
    public byte maxPressure__v;

    //氢气最高压力传感器代号
    @F_num(type = NumType.uint8, checkVal = true)
    public short maxPressureCode;
    public byte maxPressureCode__v;

    //高压DC/DC状态
    @F_num(type = NumType.uint8, checkVal = true)
    public byte dcStatus;
    public byte dcStatus__v;

    //剩余氢量百分比
    @F_num(type = NumType.uint8, checkVal = true)
    public byte percent;
    public byte percent__v;

    //高压 DC/DC控制器温度
    @F_num(type = NumType.uint8, valExpr = "x-40", checkVal = true)
    public short dcTemperature;
    public byte dcTemperature__v;

}
