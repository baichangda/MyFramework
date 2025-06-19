package cn.bcd.lib.parser.protocol.immotors.data;


import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_D014 extends Evt_4_x {
    @F_num(type = NumType.uint56)
    public long DTCInfomationICM;
    @F_num(type = NumType.uint56)
    public long DTCInfomationCARLog;
    @F_num(type = NumType.uint56)
    public long DTCInfomationIMATE;
    @F_num(type = NumType.uint56)
    public long DTCInfomationAMP;
    @F_num(type = NumType.uint56)
    public long DTCInfomationPGM;
}
