package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 极值数据
 */
public class VehicleLimitValueData {
    //最高电压电池子系统号
    @F_num(type = NumType.uint8, checkVal = true)
    public short maxVoltageSystemNo;
    public byte maxVoltageSystemNo__v;

    //最高电压电池单体代号
    @F_num(type = NumType.uint8, checkVal = true)
    public short maxVoltageCode;
    public byte maxVoltageCode__v;

    //电池单体电压最高值
    @F_num(type = NumType.uint16,  valExpr = "x/1000", checkVal = true)
    public float maxVoltage;
    public byte maxVoltage__v;

    //最低电压电池子系统号
    @F_num(type = NumType.uint8, checkVal = true)
    public short minVoltageSystemNo;
    public byte minVoltageSystemNo__v;

    //最低电压电池单体代号
    @F_num(type = NumType.uint8, checkVal = true)
    public short minVoltageCode;
    public byte minVoltageCode__v;

    //电池单体电压最低值
    @F_num(type = NumType.uint16,  valExpr = "x/1000", checkVal = true)
    public float minVoltage;
    public byte minVoltage__v;

    //最高温度子系统号
    @F_num(type = NumType.uint8, checkVal = true)
    public short maxTemperatureSystemNo;
    public byte maxTemperatureSystemNo__v;

    //最高温度探针序号
    @F_num(type = NumType.uint8, checkVal = true)
    public short maxTemperatureNo;
    public byte maxTemperatureNo__v;

    //最高温度值
    @F_num(type = NumType.uint8,  valExpr = "x-40", checkVal = true)
    public short maxTemperature;
    public byte maxTemperature__v;

    //最低温度子系统号
    @F_num(type = NumType.uint8, checkVal = true)
    public short minTemperatureSystemNo;
    public byte minTemperatureSystemNo__v;

    //最低温度探针序号
    @F_num(type = NumType.uint8, checkVal = true)
    public short minTemperatureNo;
    public byte minTemperatureNo__v;

    //最低温度值
    @F_num(type = NumType.uint8,  valExpr = "x-40", checkVal = true)
    public short minTemperature;
    public byte minTemperature__v;
}
