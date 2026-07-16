package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

public class JdkTopLevelBean {
    @F_num(type = NumType.uint8)
    public int value;
}
