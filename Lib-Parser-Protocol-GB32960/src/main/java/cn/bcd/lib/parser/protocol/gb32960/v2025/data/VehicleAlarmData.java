package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 报警数据
 */
public class VehicleAlarmData {
    //最高报警等级
    @F_num(type = NumType.uint8,checkValid = true)
    public byte maxAlarmLevel;
    public byte maxAlarmLevel__type;

    //通用报警标志
    @F_num(type = NumType.int32)
    public int alarmFlag;

    //可充电储能装置故障总数
    @F_num(type = NumType.uint8, var = 'a',checkValid = true)
    public short chargeBadNum;
    public byte chargeBadNum__type;

    //可充电储能装置故障代码列表
    @F_num_array(lenExpr = "a", singleType = NumType.uint32)
    public long[] chargeBadCodes;

    //驱动电机故障总数
    @F_num(type = NumType.uint8, var = 'b',checkValid = true)
    public short driverBadNum;
    public byte driverBadNum__type;

    //驱动电机故障代码列表
    @F_num_array(lenExpr = "b", singleType = NumType.uint32)
    public long[] driverBadCodes;

    //发动机故障总数
    @F_num(type = NumType.uint8, var = 'c',checkValid = true)
    public short engineBadNum;
    public byte engineBadNum__type;

    //发动机故障代码列表
    @F_num_array(lenExpr = "c", singleType = NumType.uint32)
    public long[] engineBadCodes;

    //其他故障总数
    @F_num(type = NumType.uint8, var = 'd',checkValid = true)
    public short otherBadNum;
    public byte otherBadNum__type;

    //其他故障代码列表
    @F_num_array(lenExpr = "d", singleType = NumType.uint32)
    public long[] otherBadCodes;

    //通用故障总数
    @F_num(type = NumType.uint8, var = 'e',checkValid = true)
    public short commonBadNum;
    public byte commonBadNum__type;

    //通用故障代码列表
    @F_num_array(lenExpr = "e", singleType = NumType.uint16)
    public int[] commonBadCodes;


}
