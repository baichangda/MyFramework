package cn.bcd.lib.parser.protocol.jtt808.v2019.data;


import cn.bcd.lib.parser.base.anno.F_date_bytes_6;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;

import java.util.Date;

public class StorageMultimediaDataUploadCmd implements PacketBody {
    //多媒体类型
    @F_num(type = NumType.uint8)
    public byte type;
    //通道id
    @F_num(type = NumType.uint8)
    public short id;
    //事件项编码
    @F_num(type = NumType.uint8)
    public byte code;
    //起始时间
    @F_date_bytes_6
    public Date startTime;
    //结束时间
    @F_date_bytes_6
    public Date endTime;
    //删除标志
    @F_num(type = NumType.uint8)
    public byte flag;
}
