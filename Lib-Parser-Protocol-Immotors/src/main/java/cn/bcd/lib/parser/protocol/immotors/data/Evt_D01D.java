package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_D01D extends Evt_4_x {
    @F_num(type = NumType.uint32)
    public long cellLAC5G;
    @F_num(type = NumType.uint64)
    public long CellID5G;
}
