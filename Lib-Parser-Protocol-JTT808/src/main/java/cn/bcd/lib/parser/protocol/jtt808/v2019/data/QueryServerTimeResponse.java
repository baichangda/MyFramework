package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.parser.base.anno.F_date_bcd;

import java.util.Date;

public class QueryServerTimeResponse implements PacketBody {
    //服务器时间
    @F_date_bcd(zoneId = "+0")
    public Date serverTime;
}
