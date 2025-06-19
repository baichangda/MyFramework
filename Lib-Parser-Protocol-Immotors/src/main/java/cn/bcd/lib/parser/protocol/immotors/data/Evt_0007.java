package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.data.BitRemainingMode;

public class Evt_0007 extends Evt_2_6 {
    @F_bit_num(len = 14,  bitRemainingMode = BitRemainingMode.ignore, unsigned = false, valExpr = "x*0.0009765625")
    public float AcceX;
    @F_bit_num(len = 14,  bitRemainingMode = BitRemainingMode.ignore, unsigned = false, valExpr = "x*0.0009765625")
    public float AcceY;
    @F_bit_num(len = 14,  bitRemainingMode = BitRemainingMode.ignore, unsigned = false, valExpr = "x*0.0009765625")
    public float AcceZ;
}
