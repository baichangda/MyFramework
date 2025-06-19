package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_skip;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_0800 extends Evt_2_6 {
    @F_bit_num(len = 2)
    public byte SysPwrMd;
    @F_bit_num(len = 1)
    public byte SysPwrMdV;
    @F_bit_num(len = 1)
    public byte SysVolV;
    @F_bit_num(len = 4)
    public byte TrShftLvrPos;
    @F_num(type = NumType.uint8, valExpr = "x*0.1+3")
    @F_skip(lenAfter = 3)
    public float SysVol;
    @F_bit_num(len = 1)
    public byte TrShftLvrPosV;
}
