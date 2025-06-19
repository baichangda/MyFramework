package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_000D extends Evt_2_6 {
    @F_num(type = NumType.uint32)
    public long CellFrequency;
}
