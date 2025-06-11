package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_customize;
import cn.bcd.lib.parser.base.anno.F_date_bytes_6;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumVal_short;
import cn.bcd.lib.parser.protocol.gb32960.v2025.processor.ParamDataProcessor;

import java.util.Date;

public class ParamQueryResponse implements PacketData {
    @F_date_bytes_6
    public Date queryTime;
    @F_num(type = NumType.uint8)
    public NumVal_short num;
    @F_customize(processorClass = ParamDataProcessor.class)
    public ParamData paramData;
}
