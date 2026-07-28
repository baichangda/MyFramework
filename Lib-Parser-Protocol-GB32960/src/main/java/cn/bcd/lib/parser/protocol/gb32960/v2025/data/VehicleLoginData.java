package cn.bcd.lib.parser.protocol.gb32960.v2025.data;


import cn.bcd.lib.parser.base.anno.*;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.protocol.gb32960.v2025.processor.VehicleLoginPackCodeProcessor;

import java.util.Date;
public class VehicleLoginData implements PacketData {
    //数据采集时间
    @F_date_bytes_6
    public Date collectTime;

    //登入流水号
    @F_num(type = NumType.uint16)
    public int sn;

    //iccid
    @F_string(len = 20)
    public String iccid;

    //电池管理系统数
    @F_num(type = NumType.uint8, var = 'n')
    public short num;

    //电池管理系统对应动力蓄电池包个数
    @F_num_array(singleType = NumType.uint8, lenExpr = "n")
    public byte[] packNums;

    //可充电储能系统编码
    @F_customize(processorClass = VehicleLoginPackCodeProcessor.class)
    public String packCodes;
}
