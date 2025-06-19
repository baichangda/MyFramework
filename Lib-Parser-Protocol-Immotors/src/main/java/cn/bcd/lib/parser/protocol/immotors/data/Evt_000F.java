package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_000F extends Evt_2_6 {
    @F_num(type = NumType.uint16, valExpr = "x*0.1-2000")
    public float TMActuToqHiPre;
    @F_bit_num(len = 15, valExpr = "x*0.1-1000")
    public float TMInvtrCrntHiPre;
}
