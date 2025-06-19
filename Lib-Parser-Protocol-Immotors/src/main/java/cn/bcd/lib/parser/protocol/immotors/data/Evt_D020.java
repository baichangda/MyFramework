package cn.bcd.lib.parser.protocol.immotors.data;


import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_D020 extends Evt_4_x {
    @F_num(type = NumType.uint56)
    public long DTCinfomationRWSL;
    @F_num(type = NumType.uint56)
    public long DTCInfomationESS;
}
