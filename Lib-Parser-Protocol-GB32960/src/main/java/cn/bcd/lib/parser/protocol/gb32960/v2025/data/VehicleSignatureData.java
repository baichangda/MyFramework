package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.anno.data.NumType;

/**
 * 车端数字签名
 */
public class VehicleSignatureData {
    @F_num(type = NumType.uint8)
    public byte type;
    @F_num(type = NumType.uint16,var = 'a')
    public int rLen;
    @F_num_array(singleType = NumType.uint8, lenExpr = "a")
    public byte[] rVal;
    @F_num(type = NumType.uint16,var = 'b')
    public int sLen;
    @F_num_array(singleType = NumType.uint8, lenExpr = "b")
    public byte[] sVal;
}
