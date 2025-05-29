package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_date_bytes_6;

import java.util.Date;

public class TimeData implements PacketData {
    @F_date_bytes_6
    public Date collectTime;
}
