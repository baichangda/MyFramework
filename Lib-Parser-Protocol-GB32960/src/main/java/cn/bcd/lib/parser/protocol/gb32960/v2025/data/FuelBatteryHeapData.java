package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

public class FuelBatteryHeapData {
    //燃料电池电堆序号
    @F_num(type = NumType.uint8, checkValid = true)
    public short no;
    public byte no__type;

    //燃料电池电堆电压
    @F_num(type = NumType.uint16, valExpr = "x/10", checkValid = true)
    public float voltage;
    public byte voltage__type;

    //燃料电池电堆电流
    @F_num(type = NumType.uint16, valExpr = "x/10", checkValid = true)
    public float current;
    public byte current__type;

    //氢气入口压力
    @F_num(type = NumType.uint16, valExpr = "(x-1000)/10", checkValid = true)
    public float hydrogenPressure;
    public byte hydrogenPressure__type;

    //空气入口压力
    @F_num(type = NumType.uint16, valExpr = "(x-1000)/10", checkValid = true)
    public float airPressure;
    public byte airPressure__type;

    //空气入口温度
    @F_num(type = NumType.uint8, valExpr = "x-40", checkValid = true)
    public short airTemperature;
    public byte airTemperature__type;

    //冷却水出水口温度探针总数
    @F_num(type = NumType.uint16, var = 'n', checkValid = true)
    public int num;
    public byte num__type;

    //冷却水出水口温度
    @F_num_array(singleType = NumType.uint8, lenExpr = "n", singleValExpr = "x-40", singleCheckValid = true)
    public short[] temperatures;
    public byte[] temperatures__type;

}
