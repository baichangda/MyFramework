package cn.bcd.lib.parser.protocol.immotors.data;


import cn.bcd.lib.parser.base.anno.F_bit_num;

public class Evt_0802 extends Evt_2_6 {
    @F_bit_num(len = 15,  valExpr = "x*0.015625")
    public float VehSpdAvgDrvn;
    @F_bit_num(len = 1)
    public byte VehSpdAvgDrvnV;
}
