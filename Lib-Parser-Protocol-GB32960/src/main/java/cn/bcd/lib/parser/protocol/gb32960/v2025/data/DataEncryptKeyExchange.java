package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import cn.bcd.lib.parser.base.anno.F_date_bytes_6;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;

import java.util.Date;

/**
 * 数据单元加密密钥交换
 */
public class DataEncryptKeyExchange implements PacketData{
    //密钥类型
    @F_num(type = NumType.uint8)
    public byte type;

    //密钥长度
    @F_num(type = NumType.uint16, var = 'n')
    public int len;

    //密钥
    @F_num_array(singleType = NumType.uint8, lenExpr = "n")
    public byte[] key;

    //启用时间
    @F_date_bytes_6
    public Date enableTime;

    //失效时间
    @F_date_bytes_6
    public Date invalidTime;
}
