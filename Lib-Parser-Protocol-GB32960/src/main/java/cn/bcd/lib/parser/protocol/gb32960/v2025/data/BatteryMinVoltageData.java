package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumVal_byte;
import cn.bcd.lib.parser.base.data.NumVal_float;
import cn.bcd.lib.parser.base.data.NumVal_int;

public class BatteryMinVoltageData {
    //动力蓄电池包号
    @F_num(type = NumType.uint8)
    public NumVal_byte no;

    //动力蓄电池包电压
    @F_num(type = NumType.uint16, valExpr = "x/10")
    public NumVal_float voltage;

    //动力蓄电池包电流
    @F_num(type = NumType.uint16, valExpr = "(x-30000)/10")
    public NumVal_float current;

    //最小并联单元总数
    @F_num(type = NumType.uint16, var = 'n')
    public NumVal_int total;

    //本帧最小并联单元电压
    @F_num_array(singleType = NumType.uint16, lenExpr = "n", singleValExpr = "x/1000")
    public NumVal_float[] minVoltages;
}
