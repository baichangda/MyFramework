package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.C_skip;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

@C_skip(lenExpr = "4+z")
public class Evt_4_x extends Evt {
    @F_num(type = NumType.uint16, var = 'z')
    public int evtLen;
}
