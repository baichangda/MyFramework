package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_000B extends Evt_2_6 {
    @F_num(type = NumType.uint8)
    public byte ModemStates;
    @F_bit_num(len = 1)
    public byte iNetworkSts;
    @F_bit_num(len = 16)
    public short iNetworkSts_ErrCode;
}
