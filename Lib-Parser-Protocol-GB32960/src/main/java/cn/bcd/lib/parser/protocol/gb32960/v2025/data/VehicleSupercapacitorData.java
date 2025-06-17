package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 超级电容器数据
 */
public class VehicleSupercapacitorData {
    //超级电容管理系统号
    @F_num(type = NumType.uint8, checkVal = true)
    public short no;
    public byte no__v;

    //超级电容总电压
    @F_num(type = NumType.uint16, valExpr = "x/10", checkVal = true)
    public float voltage;
    public byte voltage__v;

    //超级电容总电流
    @F_num(type = NumType.uint16, valExpr = "(x-30000)/10", checkVal = true)
    public float current;
    public byte current__v;

    //超级电容单体总数
    @F_num(type = NumType.uint16, var = 'm', checkVal = true)
    public int voltageNum;
    public byte voltageNum__v;

    //超级电容单体电压
    @F_num_array(singleType = NumType.uint16, lenExpr = "m", singleValExpr = "x/1000", singleCheckVal = true)
    public float[] voltages;
    public byte[] voltages__v;

    //超级电容温度探针总数
    @F_num(type = NumType.uint16, var = 'n', checkVal = true)
    public int temperatureNum;
    public byte temperatureNum__v;

    //探针温度值
    @F_num_array(singleType = NumType.uint8, lenExpr = "n", singleValExpr = "x-40", singleCheckVal = true)
    public short[] temperatures;
    public byte[] temperatures__v;
}
