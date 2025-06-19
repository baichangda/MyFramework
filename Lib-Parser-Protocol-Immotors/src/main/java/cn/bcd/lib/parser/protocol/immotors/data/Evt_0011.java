package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_bit_num;

public class Evt_0011 extends Evt_2_6 {
    @F_bit_num(len = 4)
    public byte VehMd;
    @F_bit_num(len = 1)
    public byte VehMdV;
}
