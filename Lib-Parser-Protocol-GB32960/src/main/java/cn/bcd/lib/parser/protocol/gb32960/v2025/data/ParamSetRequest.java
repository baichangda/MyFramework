package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_customize;
import cn.bcd.lib.parser.base.anno.F_date_bytes_6;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.protocol.gb32960.v2025.processor.ParamDataProcessor;

import java.util.Date;

public class ParamSetRequest implements PacketData {
    @F_date_bytes_6
    public Date setTime;
    @F_num(type = NumType.uint8,checkValid = true)
    public short num;
    public byte num__type;
    @F_customize(processorClass = ParamDataProcessor.class)
    public ParamData paramData;
}
