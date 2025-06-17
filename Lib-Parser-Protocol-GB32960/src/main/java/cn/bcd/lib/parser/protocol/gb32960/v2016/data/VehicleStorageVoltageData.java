package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.F_bean_list;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 可充电储能装置电压数据
 */
public class VehicleStorageVoltageData {
    //可充电储能子系统个数
    @F_num(type = NumType.uint8, var = 'a', checkVal = true)
    public short num;
    public byte num__v;

    //可充电储能子系统电压信息集合
    @F_bean_list(listLenExpr = "a")
    public StorageVoltageData[] content;
}
