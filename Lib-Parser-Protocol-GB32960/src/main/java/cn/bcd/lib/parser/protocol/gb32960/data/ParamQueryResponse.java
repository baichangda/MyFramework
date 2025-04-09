package cn.bcd.lib.parser.protocol.gb32960.data;

import java.util.Date;

public class ParamQueryResponse implements PacketData {
    public Date queryTime;
    public short num;
    public ParamData paramData;
}
