package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.data.NumType;

public class TempPositionFollow implements PacketBody {
    //时间间隔
    @F_num(type = NumType.uint16)
    public int interval;
    //位置跟踪有效期
    @F_num(type = NumType.uint32)
    public long valid;
}
