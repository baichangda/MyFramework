package cn.bcd.lib.parser.protocol.immotors.data;


import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_D017 extends Evt_4_x {
    @F_num(type = NumType.uint56)
    public long DTCInfomationIPS;
    @F_num(type = NumType.uint56)
    public long DTCInfomationRrDetnRdr;
    @F_num(type = NumType.uint56)
    public long DTCInfomationHUD;
    @F_num(type = NumType.uint56)
    public long DTCInfomationFLIDAR;
    @F_num(type = NumType.uint56)
    public long DTCInfomationFVCM;
    @F_num(type = NumType.uint56)
    public long DTCInfomationSPD;
}
