package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_bit_num;

public class Evt_0803 extends Evt_2_6 {
    @F_bit_num(len = 24)
    public int VehOdo;
    @F_bit_num(len = 1)
    public byte VehOdoV;
    @F_bit_num(len = 1)
    public byte BrkPdlPosV;
}
