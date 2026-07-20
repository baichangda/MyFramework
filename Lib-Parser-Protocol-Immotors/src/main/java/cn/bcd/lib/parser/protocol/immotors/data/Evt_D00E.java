package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_D00E extends Evt_4_x {
    @F_num(type = NumType.uint8, var = 'a')
    public short BMSRptBatCodeNum;
    @F_num_array(singleType = NumType.uint8,lenExpr = "a")
    public byte[] BMSRptBatCodeAsc;
}
