package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.anno.data.NumType;

public class DeleteRectangleArea implements PacketBody{
    //区域数
    @F_num(type = NumType.uint8, var = 'n')
    public short num;
    //区域id
    @F_num_array(singleType = NumType.uint32, lenExpr = "n")
    public long[] ids;
}
