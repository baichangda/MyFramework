package cn.bcd.lib.parser.protocol.immotors.data;


import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_D011 extends Evt_4_x {
    @F_num(type = NumType.uint56)
    public long DTCInfomationEPMCU;
    @F_num(type = NumType.uint56)
    public long DTCInfomationWLC;
    @F_num(type = NumType.uint56)
    public long DTCInfomationSCU;
    @F_num(type = NumType.uint56)
    public long DTCInfomationEOPC;
    @F_num(type = NumType.uint56)
    public long DTCInfomationCCU;
}
