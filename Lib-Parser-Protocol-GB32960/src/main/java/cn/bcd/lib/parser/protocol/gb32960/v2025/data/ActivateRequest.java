package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.*;
import cn.bcd.lib.parser.base.anno.data.NumType;

import java.util.Date;

/**
 * 激活信息
 */
public class ActivateRequest implements PacketData{
    //数据采集时间
    @F_date_bytes_6
    public Date collectTime;

    //芯片ID
    @F_string(len = 16)
    public String id;

    //公钥长度
    @F_num(type = NumType.uint16, var = 'n')
    public int publicKeyLen;

    //公钥
    @F_num_array(singleType = NumType.uint8, lenExpr = "n")
    public byte[] publicKey;

    //车辆识别代号(VIN)
    @F_string(len = 17)
    public String vin;

    //签名信息
    @F_bean
    public VehicleSignatureData signatureData;
}
