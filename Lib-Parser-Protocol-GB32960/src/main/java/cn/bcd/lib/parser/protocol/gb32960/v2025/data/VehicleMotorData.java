package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_bean_list;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;


/**
 * 驱动电机数据
 */
public class VehicleMotorData {
    //驱动电机个数
    @F_num(type = NumType.uint8, var = 'a', checkVal = true)
    public short num;
    public byte num__v;

    //驱动电机总成信息列表
    @F_bean_list(listLenExpr = "a")
    public MotorData[] content;
}
