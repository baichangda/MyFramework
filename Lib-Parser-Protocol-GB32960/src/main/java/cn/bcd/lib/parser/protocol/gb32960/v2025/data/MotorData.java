package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

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
    @F_num(type = NumType.uint8, checkValid = true)
    public byte status;
    public byte status__type;

    //驱动电机控制器温度
    @F_num(type = NumType.uint8, valExpr = "x-40", checkValid = true)
    public short controllerTemperature;
    public byte controllerTemperature__type;

    //驱动电机转速
    @F_num(type = NumType.uint16, valExpr = "x-32000", checkValid = true)
    public int rotateSpeed;
    public byte rotateSpeed__type;

    //驱动电机转矩
    @F_num(type = NumType.uint16, valExpr = "(x-20000)/10", checkValid = true)
    public float rotateRectangle;
    public byte rotateRectangle__type;

    //驱动电机温度
    @F_num(type = NumType.uint8, valExpr = "x-40", checkValid = true)
    public short temperature;
    public byte temperature__type;
}
