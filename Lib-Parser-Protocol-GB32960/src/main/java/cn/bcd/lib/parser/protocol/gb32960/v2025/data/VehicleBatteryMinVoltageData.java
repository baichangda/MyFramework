package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_bean_list;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 动力蓄电池最小并联单元电压数据
 */
public class VehicleBatteryMinVoltageData {
    //动力蓄电池包个数
    @F_num(type = NumType.uint8,var = 'm', checkVal = true)
    public byte num;
    public byte num__v;

    //动力蓄电池最小并联单元电压信息列表
    @F_bean_list(listLenExpr = "m")
    public BatteryMinVoltageData[] datas;
}
