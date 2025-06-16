package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 超级电容极值数据
 */
public class VehicleSupercapacitorLimitValueData {
    //最高电压管理系统号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxVoltageSystemNo;
    public byte maxVoltageSystemNo__type;

    //最高电压超级电容单体代号
    @F_num(type = NumType.uint16,checkValid = true)
    public int maxVoltageCode;
    public byte maxVoltageCode__type;

    //超级电容单体电压最高值
    @F_num(type = NumType.uint16,  valExpr = "x/1000",checkValid = true)
    public float maxVoltage;
    public byte maxVoltage__type;

    //最低电压管理系统号
    @F_num(type = NumType.uint8,checkValid = true)
    public short minVoltageSystemNo;
    public byte minVoltageSystemNo__type;

    //最低电压超级电容单体代号
    @F_num(type = NumType.uint16,checkValid = true)
    public int minVoltageCode;
    public byte minVoltageCode__type;

    //超级电容单体电压最低值
    @F_num(type = NumType.uint16,  valExpr = "x/1000",checkValid = true)
    public float minVoltage;
    public byte minVoltage__type;

    //最高温度管理系统号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxTemperatureSystemNo;
    public byte maxTemperatureSystemNo__type;

    //最高温度探针代号
    @F_num(type = NumType.uint16,checkValid = true)
    public int maxTemperatureNo;
    public byte maxTemperatureNo__type;

    //最高温度值
    @F_num(type = NumType.uint8,  valExpr = "x-40",checkValid = true)
    public short maxTemperature;
    public byte maxTemperature__type;

    //最低温度管理系统号
    @F_num(type = NumType.uint8,checkValid = true)
    public short minTemperatureSystemNo;
    public byte minTemperatureSystemNo__type;

    //最低温度探针代号
    @F_num(type = NumType.uint16,checkValid = true)
    public int minTemperatureNo;
    public byte minTemperatureNo__type;

    //最低温度值
    @F_num(type = NumType.uint8,  valExpr = "x-40",checkValid = true)
    public short minTemperature;
    public byte minTemperature__type;
}
