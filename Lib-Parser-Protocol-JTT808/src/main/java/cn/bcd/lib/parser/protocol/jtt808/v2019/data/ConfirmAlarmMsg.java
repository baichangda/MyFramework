package cn.bcd.lib.parser.protocol.jtt808.v2019.data;


import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.data.NumType;

public class ConfirmAlarmMsg implements PacketBody {
    //报警消息流水号
    @F_num(type = NumType.uint16)
    public int sn;
    //人工确认报警类型
    @F_num(type = NumType.uint32)
    public int type;
}
