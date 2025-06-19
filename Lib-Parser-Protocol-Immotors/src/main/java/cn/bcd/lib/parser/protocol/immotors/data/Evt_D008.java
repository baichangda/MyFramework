package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_D008 extends Evt_4_x {
    @F_num(type = NumType.uint56)
    public long DTCInfomationBMS;
    @F_num(type = NumType.uint56)
    public long DTCInfomationECM;
    @F_num(type = NumType.uint56)
    public long DTCInfomationEPB;
    @F_num(type = NumType.uint56)
    public long DTCInfomationPLCM;
    @F_num(type = NumType.uint56)
    public long DTCInfomationTCM;
    @F_num(type = NumType.uint56)
    public long DTCInfomationTPMS;
    @F_num(type = NumType.uint56)
    public long DTCInfomationTC;
    @F_num(type = NumType.uint56)
    public long DTCInfomationISC;
    @F_num(type = NumType.uint56)
    public long DTCInfomationSAC;
    @F_num(type = NumType.uint56)
    public long DTCInfomationIMCU;
}
