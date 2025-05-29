package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.F_customize;
import cn.bcd.lib.parser.base.anno.F_date_bytes_6;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.data.NumType;
import cn.bcd.lib.parser.protocol.gb32960.v2016.processor.ParamDataProcessor;

import java.util.Date;

public class ParamSetRequest implements PacketData{
    @F_date_bytes_6
    public Date setTime;
    @F_num(type = NumType.uint8)
    public short num;
    @F_customize(processorClass = ParamDataProcessor.class)
    public ParamData paramData;
}
