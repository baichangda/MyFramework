package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_bit_num;

public class Evt_0006 extends Evt_2_6 {
    @F_bit_num(len = 24,  valExpr = "x*0.1")
    public float HDop;
    @F_bit_num(len = 24,  valExpr = "x*0.1")
    public float VDop;
}
