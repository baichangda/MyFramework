package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumVal_int;

/**
 * 发动机数据
 */
public class VehicleEngineData {
    //曲轴转速
    @F_num(type = NumType.uint16)
    public NumVal_int speed;
}
