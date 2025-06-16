package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.*;

/**
 * 整车数据
 */
public class VehicleBaseData {
    //车辆状态
    @F_num(type = NumType.uint8,checkValid = true)
    public byte vehicleStatus;
    public byte vehicleStatus__type;

    //充电状态
    @F_num(type = NumType.uint8,checkValid = true)
    public byte chargeStatus;
    public byte chargeStatus__type;

    //运行模式
    @F_num(type = NumType.uint8,checkValid = true)
    public byte runMode;
    public byte runMode__type;

    //车速
    @F_num(type = NumType.uint16, valExpr = "x/10",checkValid = true)
    public float vehicleSpeed;
    public byte vehicleSpeed__type;

    //累计里程
    @F_num(type = NumType.uint32, valExpr = "x/10",checkValid = true)
    public double totalMileage;
    public byte totalMileage__type;

    //总电压
    @F_num(type = NumType.uint16, valExpr = "x/10",checkValid = true)
    public float totalVoltage;
    public byte totalVoltage__type;

    //总电流
    @F_num(type = NumType.uint16, valExpr = "(x-10000)/10",checkValid = true)
    public float totalCurrent;
    public byte totalCurrent__type;

    //soc
    @F_num(type = NumType.uint8,checkValid = true)
    public byte soc;
    public byte soc__type;

    //DC-DC状态
    @F_num(type = NumType.uint8,checkValid = true)
    public byte dcStatus;
    public byte dcStatus__type;

    //档位
    @F_num(type = NumType.uint8)
    public short gearPosition;

    //绝缘电阻
    @F_num(type = NumType.uint16,checkValid = true)
    public int resistance;
    public byte resistance__type;

    //加速踏板行程值
    @F_num(type = NumType.uint8,checkValid = true)
    public byte pedalVal;
    public byte pedalVal__type;

    //制动踏板状态
    @F_num(type = NumType.uint8,checkValid = true)
    public byte pedalStatus;
    public byte pedalStatus__type;
}
