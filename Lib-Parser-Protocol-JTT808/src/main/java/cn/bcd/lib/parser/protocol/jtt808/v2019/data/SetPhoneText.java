package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.parser.base.anno.F_bean_list;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.data.NumType;

public class SetPhoneText implements PacketBody {
    //设置类型
    @F_num(type = NumType.uint8)
    public byte type;
    //联系人总数
    @F_num(type = NumType.uint8, var = 'n')
    public short total;
    //联系人项
    @F_bean_list(listLenExpr = "n")
    public PhoneContactPerson[] persons;

}
