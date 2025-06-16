package cn.bcd.lib.parser.protocol.gb32960.v2016.data;


import cn.bcd.lib.parser.base.anno.F_date_bytes_6;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_string;
import cn.bcd.lib.parser.base.data.NumType;

import java.util.Date;

public class PlatformLoginData implements PacketData {
    //平台登入时间
    @F_date_bytes_6
    public Date collectTime;

    //登入流水号
    @F_num(type = NumType.uint16)
    public int sn;

    //平台用户名
    @F_string(len = 12)
    public String username;

    //平台密码
    @F_string(len = 20)
    public String password;

    //加密规则
    @F_num(type = NumType.uint8,checkValid = true)
    public byte encode;
    public byte encode__type;
}
