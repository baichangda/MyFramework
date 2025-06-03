package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.parser.base.anno.F_date_bytes_6;

import java.util.Date;

public class QueryServerTimeResponse implements PacketBody {
    //服务器时间
    @F_date_bytes_6(zoneId = "+0")
    public Date serverTime;
}
