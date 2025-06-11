package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class TerminalUpgradeResResponse implements PacketBody {
    //升级类型
    @F_num(type = NumType.uint8)
    public byte type;
    //升级结果
    @F_num(type = NumType.uint8)
    public byte res;
}
