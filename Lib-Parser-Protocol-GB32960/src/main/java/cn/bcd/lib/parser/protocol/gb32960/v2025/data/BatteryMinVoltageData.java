package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

public class BatteryMinVoltageData {
    //动力蓄电池包号
    @F_num(type = NumType.uint8, checkValid = true)
    public byte no;
    public byte no__type;

    //动力蓄电池包电压
    @F_num(type = NumType.uint16, valExpr = "x/10", checkValid = true)
    public float voltage;
    public byte voltage__type;

    //动力蓄电池包电流
    @F_num(type = NumType.uint16, valExpr = "(x-30000)/10", checkValid = true)
    public float current;
    public byte current__type;

    //最小并联单元总数
    @F_num(type = NumType.uint16, var = 'n', checkValid = true)
    public int total;
    public byte total__type;

    //本帧最小并联单元电压
    @F_num_array(singleType = NumType.uint16, lenExpr = "n", singleValExpr = "x/1000", singleCheckValid = true)
    public float[] minVoltages;
    public byte[] minVoltages__type;
}
