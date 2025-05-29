package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.anno.data.NumType;
import cn.bcd.lib.parser.base.anno.data.NumVal_float;
import cn.bcd.lib.parser.base.anno.data.NumVal_short;

/**
 * 超级电容器数据
 */
public class VehicleSupercapacitorData {
    @F_num(type = NumType.uint8)
    public NumVal_short no;

    @F_num(type = NumType.uint16, valExpr = "x10")
    public NumVal_float voltage;

    @F_num(type = NumType.uint16, valExpr = "(x-30000)/10")
    public NumVal_float current;

    @F_num(type = NumType.uint16, var = 'm')
    public int voltageNum;

    @F_num_array(singleType = NumType.uint16, lenExpr = "m", singleValExpr = "x/1000")
    public NumVal_float[] voltages;

    @F_num(type = NumType.uint16, var = 'n')
    public int temperatureNum;

    @F_num_array(singleType = NumType.uint8, lenExpr = "n", singleValExpr = "x-40")
    public NumVal_short[] temperatures;
}
