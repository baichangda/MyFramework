package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 每个驱动电机数据格式
 */
public class MotorData {
    //驱动电机序号
    @F_num(type = NumType.uint8)
    public short no;

    //驱动电机状态
    @F_num(type = NumType.uint8, checkVal = true)
    public byte status;
    public byte status__v;

    //驱动电机控制器温度
    @F_num(type = NumType.uint8, valExpr = "x-40", checkVal = true)
    public short controllerTemperature;
    public byte controllerTemperature__v;

    //驱动电机转速
    @F_num(type = NumType.uint16, valExpr = "x-20000", checkVal = true)
    public int rotateSpeed;
    public byte rotateSpeed__v;

    //驱动电机转矩
    @F_num(type = NumType.uint16, valExpr = "(x-20000)/10", checkVal = true)
    public float rotateRectangle;
    public byte rotateRectangle__v;

    //驱动电机温度
    @F_num(type = NumType.uint8, valExpr = "x-40", checkVal = true)
    public short temperature;
    public byte temperature__v;

    //电机控制器输入电压
    @F_num(type = NumType.uint16, valExpr = "x/10", checkVal = true)
    public float inputVoltage;
    public byte inputVoltage__v;

    //电机控制器直流母线电流
    @F_num(type = NumType.uint16, valExpr = "(x-10000)/10", checkVal = true)
    public float current;
    public byte current__v;
}
