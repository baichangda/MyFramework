package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_date_bytes_6;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumVal_short;

import java.util.Date;

public class ParamQueryRequest implements PacketData {
    @F_date_bytes_6
    public Date queryTime;
    @F_num(type = NumType.uint8, var = 'n')
    public NumVal_short num;
    @F_num_array(singleType = NumType.uint8, lenExpr = "n")
    public short[] paramIds;
}
