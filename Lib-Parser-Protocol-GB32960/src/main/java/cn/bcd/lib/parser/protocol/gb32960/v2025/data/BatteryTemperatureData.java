package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumVal_byte;
import cn.bcd.lib.parser.base.data.NumVal_short;

public class BatteryTemperatureData {
    //动力蓄电池包号
    @F_num(type = NumType.uint8)
    public NumVal_byte no;

    //动力蓄电池包温度探针个数
    @F_num(type = NumType.uint16,var = 'n')
    public short num;

    //各温度探针检测到的温度值
    @F_num_array(lenExpr = "n",singleType = NumType.uint8, singleValExpr = "x-40")
    public NumVal_short[] currents;
}
