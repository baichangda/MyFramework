package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumVal_byte;
import cn.bcd.lib.parser.base.data.NumVal_float;
import cn.bcd.lib.parser.base.data.NumVal_int;

/**
 * 发动机数据
 */
public class VehicleEngineData {
    //发动机状态
    @F_num(type = NumType.uint8)
    public NumVal_byte status;

    //曲轴转速
    @F_num(type = NumType.uint16)
    public NumVal_int speed;

    //燃料消耗率
    @F_num(type = NumType.uint16, valExpr = "x/100")
    public NumVal_float rate;

}
