package cn.bcd.lib.parser.protocol.jtt808.v2019.data;


import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.data.NumType;

public class SingleMultimediaDataFetchUploadCmd implements PacketBody{
    //多媒体id
    @F_num(type = NumType.uint32)
    public long id;
    //删除标志
    @F_num(type = NumType.uint8)
    public byte flag;
}
