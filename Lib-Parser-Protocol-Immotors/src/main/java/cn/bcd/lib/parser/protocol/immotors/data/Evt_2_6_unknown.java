package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_2_6_unknown extends Evt_2_6 {
    @F_num_array(len = 6, singleType = NumType.uint8)
    public byte[] data;
}
