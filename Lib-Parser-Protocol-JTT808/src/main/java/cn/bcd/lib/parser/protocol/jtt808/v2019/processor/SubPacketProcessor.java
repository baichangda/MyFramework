package cn.bcd.lib.parser.protocol.jtt808.v2019.processor;

import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.jtt808.v2019.data.PacketHeader;
import cn.bcd.lib.parser.protocol.jtt808.v2019.data.SubPacket;
import io.netty.buffer.ByteBuf;

public class SubPacketProcessor implements Processor<SubPacket> {
    @Override
    public SubPacket process(ByteBuf data, ProcessContext processContext) {
        if (((Number) processContext.getGlobalVar("subPacketFlag")).byteValue() == 0) {
            return null;
        } else {
            SubPacket subPacket = new SubPacket();
            subPacket.total = data.readUnsignedShort();
            subPacket.no = data.readUnsignedShort();
            return subPacket;
        }
    }

    @Override
    public void deProcess(ByteBuf data, ProcessContext processContext, SubPacket instance) {
        if (((Number) processContext.getGlobalVar("subPacketFlag")).byteValue() == 1) {
            data.writeShort(instance.total);
            data.writeShort(instance.no);
        }
    }
}
