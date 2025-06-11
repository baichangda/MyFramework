package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.*;
import cn.bcd.lib.parser.base.data.NumType;

/**
 * 激活结果应答
 */
public class ActivateResponse implements PacketData{
    //激活状态
    @F_num(type = NumType.uint8)
    public byte status;

    //信息
    @F_num(type = NumType.uint8)
    public byte info;
}
