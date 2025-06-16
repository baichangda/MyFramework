package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 燃料电池数据
 */
public class VehicleFuelBatteryData {

    //车载氢系统中最高温度
    @F_num(type = NumType.uint16, valExpr = "x-40",checkValid = true)
    public short maxTemperature;
    public byte maxTemperature__type;

    //车载氢系统中最中最高温度探针代号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxTemperatureCode;
    public byte maxTemperatureCode__type;

    //氢气最高浓度
    @F_num(type = NumType.uint16, valExpr = "x/10000",checkValid = true)
    public float maxConcentration;
    public byte maxConcentration__type;

    //氢气最高浓度传感器代号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxConcentrationCode;
    public byte maxConcentrationCode__type;

    //氢气最高压力
    @F_num(type = NumType.uint16, valExpr = "x/10",checkValid = true)
    public float maxPressure;
    public byte maxPressure__type;

    //氢气最高压力传感器代号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxPressureCode;
    public byte maxPressureCode__type;

    //高压DC/DC状态
    @F_num(type = NumType.uint8,checkValid = true)
    public byte dcStatus;
    public byte dcStatus__type;

    //剩余氢量百分比
    @F_num(type = NumType.uint8,checkValid = true)
    public byte percent;
    public byte percent__type;

    //高压 DC/DC控制器温度
    @F_num(type = NumType.uint8, valExpr = "x-40",checkValid = true)
    public short dcTemperature;
    public byte dcTemperature__type;

}
