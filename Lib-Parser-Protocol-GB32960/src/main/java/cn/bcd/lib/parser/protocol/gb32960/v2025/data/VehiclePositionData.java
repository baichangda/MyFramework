package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 车辆位置数据
 */
public class VehiclePositionData {
    //定位状态
    @F_num(type = NumType.uint8)
    public byte status;

    //坐标系
    @F_num(type = NumType.uint8)
    public byte type;

    //经度
    @F_num(type = NumType.uint32,  valExpr = "x/1000000")
    public double lng;

    //纬度
    @F_num(type = NumType.uint32,  valExpr = "x/1000000")
    public double lat;
}
