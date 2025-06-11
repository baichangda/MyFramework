package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumVal_float;
import cn.bcd.lib.parser.base.data.NumVal_int;
import cn.bcd.lib.parser.base.data.NumVal_short;

public class FuelBatteryHeapData {
    //燃料电池电堆序号
    @F_num(type = NumType.uint8)
    public NumVal_short no;

    //燃料电池电堆电压
    @F_num(type = NumType.uint16, valExpr = "x/10")
    public NumVal_float voltage;

    //燃料电池电堆电流
    @F_num(type = NumType.uint16, valExpr = "x/10")
    public NumVal_float current;

    //氢气入口压力
    @F_num(type = NumType.uint16, valExpr = "(x-1000)/10")
    public NumVal_float hydrogenPressure;

    //空气入口压力
    @F_num(type = NumType.uint16, valExpr = "(x-1000)/10")
    public NumVal_float airPressure;

    //空气入口温度
    @F_num(type = NumType.uint8, valExpr = "x-40")
    public NumVal_short airTemperature;

    //冷却水出水口温度探针总数
    @F_num(type = NumType.uint16, var = 'n')
    public NumVal_int num;

    //冷却水出水口温度
    @F_num_array(singleType = NumType.uint8, lenExpr = "n", singleValExpr = "x-40")
    public NumVal_short[] temperatures;

}
