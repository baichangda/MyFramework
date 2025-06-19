package cn.bcd.lib.parser.protocol.immotors.data;


import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_4_x_unknown extends Evt_4_x {
    @F_num_array(lenExpr = "z", singleType = NumType.uint8)
    public byte[] data;
}
