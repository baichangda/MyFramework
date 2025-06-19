package cn.bcd.lib.parser.protocol.immotors.data;


import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_D013 extends Evt_4_x {
    @F_num(type = NumType.uint56)
    public long DTCInfomationDCM_FL;
    @F_num(type = NumType.uint56)
    public long DTCInfomationDCM_FR;
    @F_num(type = NumType.uint56)
    public long DTCInfomationDCM_RL;
    @F_num(type = NumType.uint56)
    public long DTCInfomationDCM_RR;
    @F_num(type = NumType.uint56)
    public long DTCInfomationATC;
    @F_num(type = NumType.uint56)
    public long DTCInfomationAMR;
    @F_num(type = NumType.uint56)
    public long DTCInfomationBPEPS;
    @F_num(type = NumType.uint56)
    public long DTCInfomationMSM_Drv;
    @F_num(type = NumType.uint56)
    public long DTCInfomationMSM_Psng;
    @F_num(type = NumType.uint56)
    public long DTCInfomationDLP;
    @F_num(type = NumType.uint56)
    public long DTCInfomationBCM;
}
