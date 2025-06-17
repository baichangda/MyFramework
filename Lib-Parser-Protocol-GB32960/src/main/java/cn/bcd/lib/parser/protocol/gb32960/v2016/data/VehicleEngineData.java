package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 发动机数据
 */
public class VehicleEngineData {
    //发动机状态
    @F_num(type = NumType.uint8, checkVal = true)
    public byte status;
    public byte status__v;

    //曲轴转速
    @F_num(type = NumType.uint16, checkVal = true)
    public int speed;
    public byte speed__v;

    //燃料消耗率
    @F_num(type = NumType.uint16, valExpr = "x/100", checkVal = true)
    public float rate;
    public byte rate__v;

}
