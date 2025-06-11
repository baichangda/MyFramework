package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class CommonResponse implements PacketBody{
    //应答流水号
    @F_num(type = NumType.uint16)
    public int sn;
    //应答id
    @F_num(type = NumType.uint16)
    public int id;
    //结果
    @F_num(type = NumType.uint8)
    public byte res;
}
