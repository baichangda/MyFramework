package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 超级电容极值数据
 */
public class VehicleSupercapacitorLimitValueData {
    //最高电压管理系统号
    @F_num(type = NumType.uint8, checkVal = true)
    public short maxVoltageSystemNo;
    public byte maxVoltageSystemNo__v;

    //最高电压超级电容单体代号
    @F_num(type = NumType.uint16, checkVal = true)
    public int maxVoltageCode;
    public byte maxVoltageCode__v;

    //超级电容单体电压最高值
    @F_num(type = NumType.uint16,  valExpr = "x/1000", checkVal = true)
    public float maxVoltage;
    public byte maxVoltage__v;

    //最低电压管理系统号
    @F_num(type = NumType.uint8, checkVal = true)
    public short minVoltageSystemNo;
    public byte minVoltageSystemNo__v;

    //最低电压超级电容单体代号
    @F_num(type = NumType.uint16, checkVal = true)
    public int minVoltageCode;
    public byte minVoltageCode__v;

    //超级电容单体电压最低值
    @F_num(type = NumType.uint16,  valExpr = "x/1000", checkVal = true)
    public float minVoltage;
    public byte minVoltage__v;

    //最高温度管理系统号
    @F_num(type = NumType.uint8, checkVal = true)
    public short maxTemperatureSystemNo;
    public byte maxTemperatureSystemNo__v;

    //最高温度探针代号
    @F_num(type = NumType.uint16, checkVal = true)
    public int maxTemperatureNo;
    public byte maxTemperatureNo__v;

    //最高温度值
    @F_num(type = NumType.uint8,  valExpr = "x-40", checkVal = true)
    public short maxTemperature;
    public byte maxTemperature__v;

    //最低温度管理系统号
    @F_num(type = NumType.uint8, checkVal = true)
    public short minTemperatureSystemNo;
    public byte minTemperatureSystemNo__v;

    //最低温度探针代号
    @F_num(type = NumType.uint16, checkVal = true)
    public int minTemperatureNo;
    public byte minTemperatureNo__v;

    //最低温度值
    @F_num(type = NumType.uint8,  valExpr = "x-40", checkVal = true)
    public short minTemperature;
    public byte minTemperature__v;
}
