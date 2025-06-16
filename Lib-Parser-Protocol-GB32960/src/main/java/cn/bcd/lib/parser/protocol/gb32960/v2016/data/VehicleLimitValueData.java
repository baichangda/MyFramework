package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 极值数据
 */
public class VehicleLimitValueData {
    //最高电压电池子系统号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxVoltageSystemNo;
    public byte maxVoltageSystemNo__type;

    //最高电压电池单体代号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxVoltageCode;
    public byte maxVoltageCode__type;

    //电池单体电压最高值
    @F_num(type = NumType.uint16,  valExpr = "x/1000",checkValid = true)
    public float maxVoltage;
    public byte maxVoltage__type;

    //最低电压电池子系统号
    @F_num(type = NumType.uint8,checkValid = true)
    public short minVoltageSystemNo;
    public byte minVoltageSystemNo__type;

    //最低电压电池单体代号
    @F_num(type = NumType.uint8,checkValid = true)
    public short minVoltageCode;
    public byte minVoltageCode__type;

    //电池单体电压最低值
    @F_num(type = NumType.uint16,  valExpr = "x/1000",checkValid = true)
    public float minVoltage;
    public byte minVoltage__type;

    //最高温度子系统号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxTemperatureSystemNo;
    public byte maxTemperatureSystemNo__type;

    //最高温度探针序号
    @F_num(type = NumType.uint8,checkValid = true)
    public short maxTemperatureNo;
    public byte maxTemperatureNo__type;

    //最高温度值
    @F_num(type = NumType.uint8,  valExpr = "x-40",checkValid = true)
    public short maxTemperature;
    public byte maxTemperature__type;

    //最低温度子系统号
    @F_num(type = NumType.uint8,checkValid = true)
    public short minTemperatureSystemNo;
    public byte minTemperatureSystemNo__type;

    //最低温度探针序号
    @F_num(type = NumType.uint8,checkValid = true)
    public short minTemperatureNo;
    public byte minTemperatureNo__type;

    //最低温度值
    @F_num(type = NumType.uint8,  valExpr = "x-40",checkValid = true)
    public short minTemperature;
    public byte minTemperature__type;
}
