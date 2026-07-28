package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

public class FuelBatteryHeapData {
    //燃料电池电堆序号
    @F_num(type = NumType.uint8, checkVal = true)
    public short no;
    public byte no__v;

    //燃料电池电堆电压
    @F_num(type = NumType.uint16, valExpr = "x/10", checkVal = true)
    public float voltage;
    public byte voltage__v;

    //燃料电池电堆电流
    @F_num(type = NumType.uint16, valExpr = "x/10", checkVal = true)
    public float current;
    public byte current__v;

    //氢气入口压力
    @F_num(type = NumType.uint16, valExpr = "(x-1000)/10", checkVal = true)
    public float hydrogenPressure;
    public byte hydrogenPressure__v;

    //空气入口压力
    @F_num(type = NumType.uint16, valExpr = "(x-1000)/10", checkVal = true)
    public float airPressure;
    public byte airPressure__v;

    //空气入口温度
    @F_num(type = NumType.uint8, valExpr = "x-40", checkVal = true)
    public short airTemperature;
    public byte airTemperature__v;

    //冷却水出水口温度探针总数
    @F_num(type = NumType.uint16, var = 'n', checkVal = true)
    public int num;
    public byte num__v;

    //冷却水出水口温度
    @F_num_array(singleType = NumType.uint8, lenExpr = "n", singleValExpr = "x-40", singleCheckVal = true)
    public short[] temperatures;
    public byte[] temperatures__v;

}
