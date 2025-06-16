package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 每个可充电储能子系统电压数据格式
 */
public class StorageVoltageData {
    //可充电储能子系统号
    @F_num(type = NumType.uint8)
    public short no;

    //可充电储能装置电压
    @F_num(type = NumType.uint16, valExpr = "x/10", checkValid = true)
    public float voltage;
    public byte voltage__type;

    //可充电储能状态电流
    @F_num(type = NumType.uint16, valExpr = "(x-10000)/10", checkValid = true)
    public float current;
    public byte current__type;

    //单体电池总数
    @F_num(type = NumType.uint16, checkValid = true)
    public int total;
    public byte total__type;

    //本帧起始电池序号
    @F_num(type = NumType.uint16)
    public int frameNo;

    //本帧单体电池总数
    @F_num(type = NumType.uint8, var = 'm')
    public short frameTotal;

    //单体电池电压
    @F_num_array(singleType = NumType.uint16, lenExpr = "m", singleValExpr = "x/1000",singleCheckValid = true)
    public float[] singleVoltage;
    public byte[] singleVoltage__type;
}
