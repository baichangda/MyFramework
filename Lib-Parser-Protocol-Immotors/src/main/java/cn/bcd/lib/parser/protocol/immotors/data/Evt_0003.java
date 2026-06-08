package cn.bcd.lib.parser.protocol.immotors.data;


import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_0003 extends Evt_2_6 {
    @F_num(type = NumType.uint48)
    public long RelwakeupTim;
}
