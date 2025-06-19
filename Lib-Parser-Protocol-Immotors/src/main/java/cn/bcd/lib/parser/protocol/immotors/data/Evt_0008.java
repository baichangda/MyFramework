package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_0008 extends Evt_2_6 {
    @F_num(type = NumType.uint16)
    public int cellMCC;
    @F_num(type = NumType.uint16)
    public int cellMNC;
    @F_bit_num(len = 10)
    public short millisecond;
    @F_bit_num(len = 1)
    public byte spistatus;
}
