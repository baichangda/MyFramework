package cn.bcd.lib.parser.protocol.immotors.data;


import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_D010 extends Evt_4_x {
    @F_num(type = NumType.uint56)
    public long DTCInfomationIAM;
    @F_num(type = NumType.uint56)
    public long DTCInfomationIPD;
    @F_num(type = NumType.uint56)
    public long DTCInfomationIECU;
    @F_num(type = NumType.uint56)
    public long DTCInfomationFDR;
    @F_num(type = NumType.uint56)
    public long DTCInfomationLFSDA;
    @F_num(type = NumType.uint56)
    public long DTCInfomationRFSDA;
    @F_num(type = NumType.uint56)
    public long DTCInfomationLHRDA;
    @F_num(type = NumType.uint56)
    public long DTCInfomationRHRDA;
}
