package cn.bcd.lib.parser.protocol.immotors.data;

import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class Evt_000C extends Evt_2_6 {
    @F_bit_num(len = 4)
    public byte PotclVer;
    @F_bit_num(len = 4)
    public byte PotclSecyVer;
    @F_num(type = NumType.uint8, valExpr = "x+2000")
    public short CalendarYear;
    @F_bit_num(len = 5)
    public byte CalendarDay;
    @F_bit_num(len = 5)
    public byte CalendarMonth;
}
