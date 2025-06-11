package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumVal_float;
import cn.bcd.lib.parser.base.data.NumVal_int;
import cn.bcd.lib.parser.base.data.NumVal_short;

/**
 * 超级电容器数据
 */
public class VehicleSupercapacitorData {
    //超级电容管理系统号
    @F_num(type = NumType.uint8)
    public NumVal_short no;

    //超级电容总电压
    @F_num(type = NumType.uint16, valExpr = "x/10")
    public NumVal_float voltage;

    //超级电容总电流
    @F_num(type = NumType.uint16, valExpr = "(x-30000)/10")
    public NumVal_float current;

    //超级电容单体总数
    @F_num(type = NumType.uint16, var = 'm')
    public NumVal_int voltageNum;

    //超级电容单体电压
    @F_num_array(singleType = NumType.uint16, lenExpr = "m", singleValExpr = "x/1000")
    public NumVal_float[] voltages;

    //超级电容温度探针总数
    @F_num(type = NumType.uint16, var = 'n')
    public NumVal_int temperatureNum;

    //探针温度值
    @F_num_array(singleType = NumType.uint8, lenExpr = "n", singleValExpr = "x-40")
    public NumVal_short[] temperatures;
}
