package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.anno.F_customize;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.anno.F_string;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.gb32960.v2016.processor.PacketDataProcessor;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;


public class Packet {
    //头 0-2
    @F_num_array(len = 2, singleType = NumType.uint8)
    public byte[] header;
    //命令标识 2-3
    @F_num(type = NumType.uint8)
    public PacketFlag flag;
    //应答标识 3-4
    @F_num(type = NumType.uint8)
    public short replyFlag;
    //唯一识别码 4-21
    @F_string(len = 17)
    public String vin;
    //数据单元加密方式 21-22
    @F_num(type = NumType.uint8, checkVal = true)
    public byte encodeWay;
    public byte encodeWay__v;
    //数据单元长度 22-24
    @F_num(type = NumType.uint16)
    public int contentLength;
    @F_customize(processorClass = PacketDataProcessor.class)
    public PacketData data;
    //异或校验位
    @F_num(type = NumType.uint8)
    public byte code;


    static final Processor<Packet> processor = Parser.getProcessor(Packet.class);

    public static Packet read(ByteBuf data) {
        return processor.process(data);
    }

    public void write(ByteBuf data) {
        processor.deProcess(data, this);
    }

    /**
     * 转换为{@link ByteBuf}
     *
     * @return
     */
    public ByteBuf toByteBuf() {
        ByteBuf byteBuf = Unpooled.buffer();
        write(byteBuf);
        return byteBuf;
    }

    /**
     * 转换为{@link ByteBuf}
     * 修正数据单元长度
     * 修正异或校验位
     *
     * @return
     */
    public ByteBuf toByteBuf_fixAll() {
        ByteBuf byteBuf = toByteBuf();
        PacketUtil.fix_contentLength(byteBuf);
        PacketUtil.fix_code(byteBuf);
        return byteBuf;
    }

    /**
     * 转换为{@link ByteBuf}
     * 修正异或校验位
     *
     * @return
     */
    public ByteBuf toByteBuf_fixCode() {
        ByteBuf byteBuf = toByteBuf();
        PacketUtil.fix_code(byteBuf);
        return byteBuf;
    }

}
