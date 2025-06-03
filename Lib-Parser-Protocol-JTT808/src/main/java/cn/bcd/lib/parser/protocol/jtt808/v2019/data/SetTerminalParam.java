package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.parser.base.anno.F_bean_list;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.data.NumType;

public class SetTerminalParam implements PacketBody {
    //参数总数
    @F_num(type = NumType.uint8, var = 'n')
    public short total;
    //参数项列表
    @F_bean_list(listLenExpr = "n")
    public TerminalParamItem[] items;
}
