package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

public class BatteryTemperatureData {
    //动力蓄电池包号
    @F_num(type = NumType.uint8, checkVal = true)
    public byte no;
    public byte no__v;

    //动力蓄电池包温度探针个数
    @F_num(type = NumType.uint16, var = 'n', checkVal = true)
    public int num;
    public byte num__v;

    //各温度探针检测到的温度值
    @F_num_array(lenExpr = "n", singleType = NumType.uint8, singleValExpr = "x-40", singleCheckVal = true)
    public short[] currents;
    public byte[] currents__v;
}
