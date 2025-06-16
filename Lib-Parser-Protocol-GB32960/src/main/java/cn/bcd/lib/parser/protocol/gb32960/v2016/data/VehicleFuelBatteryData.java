package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.*;

/**
 * 燃料电池数据
 */
public class VehicleFuelBatteryData {
    //燃料电池电压
    @F_num(type = NumType.uint16, valExpr = "x/10",checkValid = true)
    public float voltage;
    public byte voltage__type;

    //燃料电池电流
    @F_num(type = NumType.uint16, valExpr = "x/10",checkValid = true)
    public float current;
    public byte current__type;

    //燃料消耗率
    @F_num(type = NumType.uint16, valExpr = "x/100",checkValid = true)
    public float consumptionRate;
    public byte consumptionRate__type;

    //燃料电池温度探针总数
    @F_num(type = NumType.uint16, var = 'a',checkValid = true)
    public int num;
    public byte num__type;

    //探针温度值
    @F_num_array(lenExpr = "a", singleValExpr = "x-40", singleType = NumType.uint8)
    public short[] temperatures;

    //氢系统中最高温度
    @F_num(type = NumType.uint16,  valExpr = "(x-400)/10",checkValid = true)
    public float maxTemperature;
    public byte maxTemperature__type;

    //氢系统中最高温度探针代号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxTemperatureCode;
    public byte maxTemperatureCode__type;

    //氢气最高浓度
    @F_num(type = NumType.uint16,  valExpr = "x-10000",checkValid = true)
    public int maxConcentration;
    public byte maxConcentration__type;

    //氢气最高浓度传感器代号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxConcentrationCode;
    public byte maxConcentrationCode__type;

    //氢气最高压力
    @F_num(type = NumType.uint16, valExpr = "x/10")
    public float maxPressure;

    //氢气最高压力传感器代号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxPressureCode;
    public byte maxPressureCode__type;

    //高压DC/DC状态
    @F_num(type = NumType.uint8,checkValid = true)
    public byte dcStatus;
    public byte dcStatus__type;

}
