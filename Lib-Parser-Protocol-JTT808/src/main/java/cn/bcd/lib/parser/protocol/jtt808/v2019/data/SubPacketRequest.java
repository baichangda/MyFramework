package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

public class SubPacketRequest implements PacketBody {
    //原始消息流水号
    @F_num(type = NumType.uint16)
    public int sn;
    //重传包总数
    @F_num(type = NumType.uint16, var = 'n')
    public int total;
    //重传包id列表
    @F_num_array(singleType = NumType.uint16, lenExpr = "n")
    public int[] ids;
}
