package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

public class TerminalParamItem {
    //参数id
    @F_num(type = NumType.uint32)
    public long id;
    //参数长度
    @F_num(type = NumType.uint8, var = 'n')
    public short len;
    //参数值
    @F_num_array(singleType = NumType.uint8, lenExpr = "n")
    public byte[] val;
}
