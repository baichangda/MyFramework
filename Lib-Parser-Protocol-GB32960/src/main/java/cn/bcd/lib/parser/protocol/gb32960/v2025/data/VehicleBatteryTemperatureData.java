package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_bean_list;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 动力蓄电池温度数据
 */
public class VehicleBatteryTemperatureData {
    //动力蓄电池包个数
    @F_num(type = NumType.uint8,var = 'n',checkValid = true)
    public byte num;
    public byte num__type;

    //动力蓄电池温度信息列表
    @F_bean_list(listLenExpr = "n")
    public BatteryTemperatureData[] datas;
}
