package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_bean_list;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumVal_short;

/**
 * 燃料电池电堆数据
 */
public class VehicleFuelBatteryHeapData {

    //燃料电池电堆个数
    @F_num(type = NumType.uint8, var = 'n')
    public NumVal_short num;

    //燃料电池电堆信息表
    @F_bean_list(listLenExpr = "n")
    public FuelBatteryHeapData[] datas;

}
