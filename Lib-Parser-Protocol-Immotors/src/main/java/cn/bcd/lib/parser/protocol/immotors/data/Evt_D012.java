package cn.bcd.lib.parser.protocol.immotors.data;


import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_D012 extends Evt_4_x {
    @F_num(type = NumType.uint56)
    public long DTCInfomationSDM;
    @F_num(type = NumType.uint56)
    public long DTCInfomationIBS;
    @F_num(type = NumType.uint56)
    public long DTCInfomationEPS;
    @F_num(type = NumType.uint56)
    public long DTCInfomationEPS_S;
    @F_num(type = NumType.uint56)
    public long DTCInfomationSCM;
    @F_num(type = NumType.uint56)
    public long DTCInfomationRBM;
    @F_num(type = NumType.uint56)
    public long DTCInfomationSAS;
    @F_num(type = NumType.uint56)
    public long DTCInfomationRWSGW;
    @F_num(type = NumType.uint56)
    public long DTCInfomationRWS;
}
