package cn.bcd.parser.protocol.gb32960.data;

import cn.bcd.parser.base.anno.F_date_bytes_6;

import java.util.Date;

public class ResponseData implements PacketData {
    @F_date_bytes_6
    public Date collectTime;
}
